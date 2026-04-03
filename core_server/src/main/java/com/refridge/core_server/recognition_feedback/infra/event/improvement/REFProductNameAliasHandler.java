package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackBrandCorrectionCountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 제품명이 변경된 부정 피드백을 처리합니다.
 *
 * <h3>Redis miss 복원 전략</h3>
 * alias 후보 Hash(feedback:product-alias:{originalName})에
 * correctedName 필드가 처음 생성되는 경우(increment 결과 = 1),
 * 해당 Hash 자체가 TTL 만료 후 첫 접근임을 의미합니다.
 *
 * 이 경우 DB에서 원본 제품명 기준 수정 제품명별 누적 횟수를 조회하여
 * Hash 전체를 복원합니다. __total__도 CORRECTED 피드백 총 건수로 복원합니다.
 * (긍정 피드백에 의한 __total__ 증가분은 REFPositiveFeedbackAggregationHandler가
 *  TTL 갱신과 함께 다시 쌓아가므로 별도 복원하지 않습니다.)
 *
 * Redis가 살아있는 경우 DB 조회 없이 Hash increment만 수행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameAliasHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFAliasConfirmationService aliasConfirmationService;
    private final REFRecognitionFeedbackRepository feedbackRepository;

    private static final String ALIAS_CANDIDATE_PREFIX = "feedback:product-alias:";
    private static final Duration CANDIDATE_TTL = Duration.ofDays(30);

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.PRODUCT_NAME;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String originalName = event.snapshot().getProductName();
        String correctedName = event.correction().getCorrectedProductName();

        if (originalName == null || correctedName == null) return;
        if (originalName.equals(correctedName)) return;

        String hashKey = ALIAS_CANDIDATE_PREFIX + originalName;

        // ── Step 1: 수정본 선택 횟수 +1 ──────────────────────────
        Long occurrenceCount = redisTemplate.opsForHash()
                .increment(hashKey, correctedName, 1);

        // ── Step 2: 전체 선택 횟수 +1 ────────────────────────────
        Long totalCount = redisTemplate.opsForHash()
                .increment(hashKey, REFAliasConfirmationService.TOTAL_FIELD, 1);

        if (occurrenceCount == null || totalCount == null) {
            log.warn("[Alias 핸들러] Redis increment 실패. originalName='{}'", originalName);
            return;
        }

        // ── Step 3: Redis miss 복원 ───────────────────────────────
        // occurrenceCount == 1 = 이 correctedName 필드가 처음 생성됨
        //   = Hash 자체가 TTL 만료 후 첫 접근 (or 진짜 첫 피드백)
        // DB에서 실제 누적 횟수를 조회하여 Hash 전체를 복원
        if (occurrenceCount == 1) {
            List<REFFeedbackBrandCorrectionCountDto> dbCounts =
                    feedbackRepository.findAliasCandidateCountsByOriginalName(originalName);

            if (!dbCounts.isEmpty()) {
                // DB 데이터가 있으면 TTL 만료 후 재진입 → Hash 복원
                long dbTotal = 0L;
                for (REFFeedbackBrandCorrectionCountDto dto : dbCounts) {
                    redisTemplate.opsForHash().put(
                            hashKey,
                            dto.correctedProductName(),
                            String.valueOf(dto.selectionCount())
                    );
                    dbTotal += dto.selectionCount();
                }
                // __total__을 CORRECTED 피드백 합계로 복원
                // (긍정 피드백 분은 REFPositiveFeedbackAggregationHandler가 다시 쌓음)
                redisTemplate.opsForHash().put(
                        hashKey,
                        REFAliasConfirmationService.TOTAL_FIELD,
                        String.valueOf(dbTotal)
                );

                // 복원된 값으로 occurrenceCount/totalCount 재설정
                occurrenceCount = dbCounts.stream()
                        .filter(d -> correctedName.equals(d.correctedProductName()))
                        .mapToLong(REFFeedbackBrandCorrectionCountDto::selectionCount)
                        .findFirst()
                        .orElse(1L);
                totalCount = dbTotal;

                log.info("[Alias 핸들러] Redis 복원. originalName='{}', 후보수={}, total={}",
                        originalName, dbCounts.size(), dbTotal);
            }
        }

        // ── Step 4: TTL 갱신 ──────────────────────────────────────
        redisTemplate.expire(hashKey, CANDIDATE_TTL);

        log.info("[Alias 핸들러] '{}' → '{}', 이 후보={}회, 전체={}회, feedbackId={}",
                originalName, correctedName, occurrenceCount, totalCount,
                event.feedbackId().getValue());

        // ── Step 5: Gate 1 빠른 차단 ──────────────────────────────
        if (occurrenceCount < REFAliasConfirmationService.MIN_ALIAS_COUNT) return;

        // ── Step 6: HGETALL → 3중 게이트 검사 ────────────────────
        Map<String, Long> allCounts = aliasConfirmationService.getAllCandidateCounts(hashKey);

        boolean meetsThreshold = aliasConfirmationService.meetsConfirmationThreshold(
                correctedName, occurrenceCount, totalCount, allCounts);

        if (meetsThreshold) {
            aliasConfirmationService.confirmAlias(
                    originalName, correctedName, occurrenceCount, totalCount);
            log.info("[Alias 핸들러] alias 확정. '{}' → '{}'", originalName, correctedName);
        } else {
            if (aliasConfirmationService.isConfirmed(originalName)) {
                log.info("[Alias 핸들러] 경쟁 후보 재부상, reopen. originalName='{}'", originalName);
                aliasConfirmationService.reopenAlias(originalName);
            }
        }
    }
}