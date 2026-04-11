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
 * <h3>제품명 수정 피드백 핸들러</h3>
 * 제품명이 변경된 부정 피드백을 처리하는 개선 핸들러입니다.
 *
 * <h3>진입 경로</h3>
 * <p>
 * {@link REFNegativeFeedbackDispatcher}가 {@code REFCorrectionDiff}를 분석하여
 * {@code changedFields}에 {@link REFCorrectionType#PRODUCT_NAME}이 포함된 경우
 * 이 핸들러의 {@link #handle(REFNegativeFeedbackEvent)}를 호출합니다.
 * </p>
 *
 * <h3>핵심 역할 — alias 후보 집계 및 자동 확정</h3>
 * <p>
 * 사용자가 수정한 제품명을 Redis Hash에 누적하고,
 * 3중 게이트 조건을 충족하면 해당 수정본을 공식 alias로 자동 확정합니다.
 * 확정된 alias는 이후 인식 파이프라인 응답에서 원본 제품명 대신 노출됩니다.
 * </p>
 *
 * <h3>전체 처리 흐름</h3>
 * <pre>
 * REFNegativeFeedbackDispatcher
 *   │
 *   └─▶ handle(event)
 *         │
 *         ├─ incrementCandidateCounts()
 *         │     ├─ Redis HINCR: correctedName 선택 횟수 +1
 *         │     └─ Redis HINCR: __total__ +1
 *         │
 *         ├─ restoreFromDbIfMiss()
 *         │     └─ occurrenceCount == 1?
 *         │           ├─ Case A (첫 피드백): DB 비어있음 → 그대로 진행
 *         │           └─ Case B (TTL 만료):  DB 이력 존재 → Hash 전체 복원
 *         │                 __total__ = CORRECTED 합계 + APPROVED 합계 (이슈 2.2)
 *         │
 *         ├─ TTL 갱신 (30일)
 *         │
 *         └─ evaluateAndConfirmAlias()
 *               ├─ Gate 1: occurrenceCount < MIN_ALIAS_COUNT(10) → 즉시 리턴
 *               ├─ Gate 2: occurrenceCount / __total__ >= 0.7
 *               ├─ Gate 3: occurrenceCount / 2위 후보 >= 3.0
 *               │
 *               ├─ 통과 → confirmAlias()
 *               └─ 미달 + CONFIRMED 상태 → reopenAlias()
 * </pre>
 *
 * <h3>Redis 데이터 구조</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead>
 *     <tr><th>키</th><th>타입</th><th>TTL</th><th>설명</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code feedback:product-alias:{originalName}}</td>
 *       <td>Hash</td>
 *       <td>30일 (활동 시 갱신)</td>
 *       <td>수정본별 선택 횟수 + {@code __total__} 필드</td>
 *     </tr>
 *     <tr>
 *       <td>{@code alias:confirmed}</td>
 *       <td>Hash</td>
 *       <td>없음</td>
 *       <td>원본명 → 확정 alias명 매핑 (REFAliasConfirmationService 관리)</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>__total__ 설계 의도</h3>
 * <p>
 * {@code __total__}은 부정 피드백(수정본 선택)뿐 아니라
 * 긍정 피드백(승인)도 포함한 전체 사용자 반응 수입니다.
 * Gate 2의 분모로 사용되어, 다수의 정상 사용자가 승인한 경우
 * 소수 악용 사용자의 수정본이 0.7 비율을 넘지 못하도록 방어합니다.
 * </p>
 *
 * <h3>TTL 만료 후 Redis miss 복원 전략 (이슈 2.2 수정)</h3>
 * <p>
 * {@code __total__}을 CORRECTED 피드백 합계로만 복원하면 과거 APPROVED(긍정) 피드백이
 * 누락되어 Gate 2 비율이 실제보다 크게 계산됩니다. 예를 들어, 긍정 50건·부정 12건인 경우
 * 정상 비율은 12/62 = 19.4%이나 복원 후에는 12/12 = 100%가 되어 오확정이 발생합니다.
 * 수정 후에는 DB에서 APPROVED 건수도 함께 조회하여 {@code __total__}에 합산합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 3.
 * @see REFNegativeFeedbackDispatcher
 * @see REFAliasConfirmationService
 * @see com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameAliasHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFAliasConfirmationService aliasConfirmationService;
    private final REFRecognitionFeedbackRepository feedbackRepository;

    private static final String ALIAS_CANDIDATE_PREFIX = "feedback:product-alias:";

    /**
     * 30일간 해당 제품명에 대한 피드백 활동이 없으면 Hash 자동 만료.
     * 만료 후 첫 피드백 도달 시 DB에서 복원 ({@link #restoreFromDbIfMiss} 참고).
     */
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

        CandidateCounts counts = incrementCandidateCounts(hashKey, correctedName);
        if (counts == null) return;

        counts = restoreFromDbIfMiss(hashKey, originalName, correctedName, counts);

        redisTemplate.expire(hashKey, CANDIDATE_TTL);

        log.info("[Alias 핸들러] '{}' → '{}', 이 후보={}회, 전체={}회, feedbackId={}",
                originalName, correctedName,
                counts.occurrenceCount(), counts.totalCount(),
                event.feedbackId().getValue());

        evaluateAndConfirmAlias(hashKey, originalName, correctedName, counts);
    }

    /**
     * Redis Hash에 수정본 선택 횟수와 전체 반응 횟수를 각각 1씩 증가시킵니다.
     */
    private CandidateCounts incrementCandidateCounts(String hashKey, String correctedName) {
        Long occurrenceCount = redisTemplate.opsForHash()
                .increment(hashKey, correctedName, 1);
        Long totalCount = redisTemplate.opsForHash()
                .increment(hashKey, REFAliasConfirmationService.TOTAL_FIELD, 1);

        if (occurrenceCount == null || totalCount == null) {
            log.warn("[Alias 핸들러] Redis increment 실패. hashKey='{}'", hashKey);
            return null;
        }

        return new CandidateCounts(occurrenceCount, totalCount);
    }

    /**
     * TTL 만료 후 첫 접근인 경우 DB에서 alias 후보 이력을 조회하여 Hash를 복원합니다.
     *
     * <h3>케이스 분기</h3>
     * <ul>
     *   <li><b>Case A (진짜 첫 피드백):</b> DB가 비어있음 → 복원 없이 그대로 반환</li>
     *   <li><b>Case B (TTL 만료 후 재진입):</b> DB에 이전 이력 존재 → Hash 전체 복원 후 반환</li>
     * </ul>
     *
     * <h3>[이슈 2.2 수정] __total__ 복원 시 APPROVED 합산</h3>
     * <p>
     * 기존에는 {@code __total__}을 CORRECTED 피드백 합계로만 복원했습니다.
     * 이 경우 과거 APPROVED(긍정 피드백)가 분모에서 누락되어 Gate 2 비율이
     * 실제보다 크게 계산되는 오확정 문제가 발생합니다.
     * 수정 후에는 DB에서 APPROVED 건수도 조회하여 {@code __total__}에 합산합니다.
     * (쿼리 추가 1건 — TTL 만료 후 첫 진입 시에만 실행되므로 핫패스에 영향 없음)
     * </p>
     */
    private CandidateCounts restoreFromDbIfMiss(String hashKey,
                                                String originalName,
                                                String correctedName,
                                                CandidateCounts counts) {
        if (counts.occurrenceCount() != 1) return counts;

        List<REFFeedbackBrandCorrectionCountDto> dbCounts =
                feedbackRepository.findAliasCandidateCountsByOriginalName(originalName);

        if (dbCounts.isEmpty()) return counts;

        long dbTotal = 0L;
        for (REFFeedbackBrandCorrectionCountDto dto : dbCounts) {
            redisTemplate.opsForHash().put(
                    hashKey,
                    dto.correctedProductName(),
                    String.valueOf(dto.selectionCount())
            );
            dbTotal += dto.selectionCount();
        }

        // 기존: __total__ = CORRECTED 합계 → 긍정 피드백 누락으로 비율 과대 계산
        // 수정: __total__ = CORRECTED 합계 + APPROVED 합계
        long approvedCount = feedbackRepository.countApprovedByOriginalProductName(originalName);
        long restoredTotal = dbTotal + approvedCount;

        redisTemplate.opsForHash().put(
                hashKey,
                REFAliasConfirmationService.TOTAL_FIELD,
                String.valueOf(restoredTotal)
        );

        long restoredOccurrence = dbCounts.stream()
                .filter(d -> correctedName.equals(d.correctedProductName()))
                .mapToLong(REFFeedbackBrandCorrectionCountDto::selectionCount)
                .findFirst()
                .orElse(1L);

        log.info("[Alias 핸들러] Redis 복원. originalName='{}', 후보수={}, correctedTotal={}, " +
                        "approvedCount={}, restoredTotal={}",
                originalName, dbCounts.size(), dbTotal, approvedCount, restoredTotal);

        return new CandidateCounts(restoredOccurrence, restoredTotal);
    }

    /**
     * 3중 게이트를 검사하여 alias 확정 또는 reopen을 처리합니다.
     */
    private void evaluateAndConfirmAlias(String hashKey,
                                         String originalName,
                                         String correctedName,
                                         CandidateCounts counts) {
        if (counts.occurrenceCount() < REFAliasConfirmationService.MIN_ALIAS_COUNT) return;

        Map<String, Long> allCounts = aliasConfirmationService.getAllCandidateCounts(hashKey);

        boolean meetsThreshold = aliasConfirmationService.meetsConfirmationThreshold(
                correctedName, counts.occurrenceCount(), counts.totalCount(), allCounts);

        if (meetsThreshold) {
            aliasConfirmationService.confirmAlias(
                    originalName, correctedName,
                    counts.occurrenceCount(), counts.totalCount());
            log.info("[Alias 핸들러] alias 확정. '{}' → '{}'", originalName, correctedName);

        } else if (aliasConfirmationService.isConfirmed(originalName)) {
            log.info("[Alias 핸들러] 경쟁 후보 재부상, reopen. originalName='{}'", originalName);
            aliasConfirmationService.reopenAlias(originalName);
        }
    }

    /**
     * Redis HINCR 결과로 얻은 수정본 선택 횟수와 전체 반응 횟수를 함께 전달하기 위한
     * 불변 값 객체입니다.
     */
    private record CandidateCounts(long occurrenceCount, long totalCount) {}
}