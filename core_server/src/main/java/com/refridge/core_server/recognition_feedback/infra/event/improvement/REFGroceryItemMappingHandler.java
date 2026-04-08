package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackGroceryItemMappingCountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 식재료명이 변경된 부정 피드백을 처리하는 개선 핸들러입니다.
 *
 * <h3>핵심 역할 — 식재료명 교정 투표 집계 및 자동 확정</h3>
 * <p>
 * {@link REFProductNameAliasHandler}가 제품명 alias를 관리하듯,
 * 이 핸들러는 "파이프라인 인식 식재료명 → 사용자 교정 식재료명" 투표를
 * Redis Hash에 누적하고 3중 게이트 조건 충족 시 {@link REFGroceryItemCorrectionService}에
 * 확정을 위임합니다.
 * </p>
 *
 * <h3>전체 처리 흐름</h3>
 * <pre>
 * REFNegativeFeedbackDispatcher
 *   │
 *   └─▶ handle(event)
 *         │
 *         ├─ [Step 1] incrementCandidateCounts()
 *         │     ├─ Redis HINCR: correctedGroceryItemName 선택 횟수 +1
 *         │     └─ Redis HINCR: __total__ +1
 *         │
 *         ├─ [Step 2] restoreFromDbIfMiss()
 *         │     └─ occurrenceCount == 1?
 *         │           ├─ Case A (첫 피드백): DB 비어있음 → 그대로 진행
 *         │           └─ Case B (TTL 만료):  DB 이력 존재 → Hash 전체 복원
 *         │
 *         ├─ [Step 3] TTL 갱신 (30일)
 *         │
 *         ├─ [Step 4] enqueueForReview() — 신규 식재료 가능성 검수 큐 적재
 *         │
 *         └─ [Step 5] evaluateAndConfirmCorrection()
 *               ├─ Gate 1 빠른 차단: occurrenceCount < MIN_CORRECTION_COUNT(10)
 *               ├─ HGETALL → Gate 2, Gate 3 검사
 *               ├─ 통과 → REFGroceryItemCorrectionService.confirmCorrection()
 *               │           └─ DB 저장 + Redis 반영 + 이벤트 발행
 *               └─ 미달 + CONFIRMED → reopenCorrection()
 * </pre>
 *
 * <h3>Redis 데이터 구조</h3>
 * <pre>
 *   Hash: feedback:grocery-item-correction:{originalGroceryItemName}
 *     Field: {correctedName1} → 선택 횟수
 *     Field: __total__        → 전체 반응 횟수 (긍정 피드백 포함)
 *     TTL: 30일
 * </pre>
 *
 * <h3>__total__ 관리 분담</h3>
 * <ul>
 *   <li>부정 피드백(이 핸들러): 수정본 선택 시 correctedName +1, __total__ +1</li>
 *   <li>긍정 피드백({@link REFPositiveFeedbackAggregationHandler}): 승인 시 __total__ +1</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 * @see REFGroceryItemCorrectionService
 * @see REFPositiveFeedbackAggregationHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemMappingHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFFeedbackReviewItemRepository reviewItemRepository;
    private final REFGroceryItemCorrectionService correctionService;
    private final REFRecognitionFeedbackRepository feedbackRepository;

    private static final Duration CANDIDATE_TTL = Duration.ofDays(30);

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.GROCERY_ITEM;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String originalGroceryItem = event.snapshot().getGroceryItemName();
        String correctedGroceryItem = event.correction().getCorrectedGroceryItemName();
        String completedBy = event.snapshot().getCompletedBy();

        if (correctedGroceryItem == null || correctedGroceryItem.isBlank()) return;
        if (correctedGroceryItem.equals(originalGroceryItem)) return;

        String hashKey = REFGroceryItemCorrectionService.CORRECTION_CANDIDATE_PREFIX
                + originalGroceryItem;

        // Step 1: Redis Hash 카운터 증가
        CandidateCounts counts = incrementCandidateCounts(hashKey, correctedGroceryItem);
        if (counts == null) return;

        // Step 2: TTL 만료 후 재진입이면 DB에서 Hash 복원
        counts = restoreFromDbIfMiss(hashKey, originalGroceryItem, correctedGroceryItem, counts);

        // Step 3: TTL 갱신 (restoreFromDbIfMiss 이후 실행하여 복원 직후 TTL 즉시 세팅)
        redisTemplate.expire(hashKey, CANDIDATE_TTL);

        log.info("[식재료명 교정] '{}' → '{}', 이 후보={}회, 전체={}회, feedbackId={}",
                originalGroceryItem, correctedGroceryItem,
                counts.occurrenceCount(), counts.totalCount(),
                event.feedbackId().getValue());

        // Step 4: 신규 식재료 가능성 검수 큐 적재
        enqueueForReview(event, correctedGroceryItem, completedBy);

        // Step 5: 3중 게이트 검사 및 교정 확정/reopen
        evaluateAndConfirmCorrection(hashKey, originalGroceryItem, correctedGroceryItem, counts);
    }

    /**
     * Redis Hash에 수정본 선택 횟수와 전체 반응 횟수를 각각 1씩 증가시킵니다.
     *
     * @return 증가 후 현재 카운트. Redis 장애 시 {@code null} 반환
     */
    private CandidateCounts incrementCandidateCounts(String hashKey, String correctedGroceryItem) {
        Long occurrenceCount = redisTemplate.opsForHash()
                .increment(hashKey, correctedGroceryItem, 1);
        Long totalCount = redisTemplate.opsForHash()
                .increment(hashKey, REFGroceryItemCorrectionService.TOTAL_FIELD, 1);

        if (occurrenceCount == null || totalCount == null) {
            log.warn("[식재료명 교정] Redis increment 실패. hashKey='{}'", hashKey);
            return null;
        }

        return new CandidateCounts(occurrenceCount, totalCount);
    }

    /**
     * TTL 만료 후 첫 접근인 경우 DB에서 교정 이력을 조회하여 Hash를 복원합니다.
     *
     * <h3>케이스 분기</h3>
     * <ul>
     *   <li><b>Case A (진짜 첫 피드백):</b> DB가 비어있음 → 복원 없이 그대로 반환</li>
     *   <li><b>Case B (TTL 만료 후 재진입):</b> DB에 이전 이력 존재 → Hash 전체 복원 후 반환</li>
     * </ul>
     * <p>
     * {@code occurrenceCount >= 2}이면 Redis 살아있는 정상 케이스이므로 DB를 조회하지 않습니다.
     * </p>
     */
    private CandidateCounts restoreFromDbIfMiss(String hashKey,
                                                String originalGroceryItem,
                                                String correctedGroceryItem,
                                                CandidateCounts counts) {
        // occurrenceCount >= 2이면 Redis 살아있는 정상 케이스 — DB 조회 불필요
        if (counts.occurrenceCount() != 1) return counts;

        List<REFFeedbackGroceryItemMappingCountDto> dbCounts =
                feedbackRepository.findGroceryItemMappingCountsByOriginalName(originalGroceryItem);

        // Case A: 진짜 첫 피드백
        if (dbCounts.isEmpty()) return counts;

        // Case B: TTL 만료 후 재진입 → DB 데이터로 Hash 전체 복원
        long dbTotal = 0L;
        for (REFFeedbackGroceryItemMappingCountDto dto : dbCounts) {
            redisTemplate.opsForHash().put(
                    hashKey,
                    dto.correctedGroceryItemName(),
                    String.valueOf(dto.selectionCount())
            );
            dbTotal += dto.selectionCount();
        }

        // __total__을 DB 집계값으로 복원
        // 긍정 피드백 분은 REFPositiveFeedbackAggregationHandler가 이후 자연스럽게 재누적
        redisTemplate.opsForHash().put(
                hashKey,
                REFGroceryItemCorrectionService.TOTAL_FIELD,
                String.valueOf(dbTotal)
        );

        // 현재 처리 중인 correctedGroceryItem이 DB에 없으면 occurrenceCount는 1로 유지
        long restoredOccurrence = dbCounts.stream()
                .filter(d -> correctedGroceryItem.equals(d.correctedGroceryItemName()))
                .mapToLong(REFFeedbackGroceryItemMappingCountDto::selectionCount)
                .findFirst()
                .orElse(1L);

        log.info("[식재료명 교정] Redis 복원. originalGroceryItem='{}', 후보수={}, total={}",
                originalGroceryItem, dbCounts.size(), dbTotal);

        return new CandidateCounts(restoredOccurrence, dbTotal);
    }

    /**
     * 3중 게이트를 검사하여 교정 확정 또는 reopen을 처리합니다.
     *
     * <h3>Gate 1 빠른 차단</h3>
     * <p>
     * HGETALL 비용을 피하기 위해 Gate 1을 먼저 검사합니다.
     * Gate 1 통과 시에만 HGETALL을 실행합니다.
     * </p>
     */
    private void evaluateAndConfirmCorrection(String hashKey,
                                              String originalGroceryItem,
                                              String correctedGroceryItem,
                                              CandidateCounts counts) {
        // Gate 1 빠른 차단
        if (counts.occurrenceCount() < REFGroceryItemCorrectionService.MIN_CORRECTION_COUNT) {
            return;
        }

        // Gate 1 통과 시에만 HGETALL 실행
        Map<String, Long> allCounts = correctionService.getAllCandidateCounts(hashKey);

        boolean meetsThreshold = correctionService.meetsConfirmationThreshold(
                correctedGroceryItem,
                counts.occurrenceCount(),
                counts.totalCount(),
                allCounts
        );

        if (meetsThreshold) {
            // DB 저장 + Redis 반영 + 이벤트 발행
            correctionService.confirmCorrection(
                    originalGroceryItem,
                    correctedGroceryItem,
                    counts.occurrenceCount(),
                    counts.totalCount()
            );

        } else if (correctionService.isConfirmed(originalGroceryItem)) {
            // 경쟁 후보 재부상 → reopen
            correctionService.reopenCorrection(originalGroceryItem);
            log.info("[식재료명 교정] 경쟁 후보 재부상, reopen. originalGroceryItem='{}'",
                    originalGroceryItem);
        }
    }

    /**
     * 신규 식재료 후보를 검수 큐에 적재합니다.
     *
     * <p>
     * 이미 동일한 {@code correctedGroceryItem}이 PENDING 상태로 존재하면
     * {@code occurrenceCount}만 증가시킵니다 (중복 적재 방지).
     * {@code sourceHandlerName}을 기록하여 관리자 승인 시
     * MLPrediction 여부 판단에 활용합니다.
     * </p>
     */
    private void enqueueForReview(REFNegativeFeedbackEvent event,
                                  String correctedGroceryItem,
                                  String completedBy) {
        String context = String.format(
                "원본식재료='%s', 수정식재료='%s', completedBy='%s', " +
                        "카테고리동시변경=%s, 원본제품명='%s'",
                event.snapshot().getGroceryItemName(),
                correctedGroceryItem,
                completedBy,
                event.diff().isCategoryChanged(),
                event.snapshot().getProductName()
        );

        reviewItemRepository.findByReviewTypeAndTargetValue(
                        REFReviewType.NEW_GROCERY_ITEM, correctedGroceryItem)
                .ifPresentOrElse(
                        REFFeedbackReviewItem::incrementOccurrence,
                        () -> reviewItemRepository.save(
                                REFFeedbackReviewItem.create(
                                        REFReviewType.NEW_GROCERY_ITEM,
                                        correctedGroceryItem,
                                        context,
                                        event.feedbackId().getValue(),
                                        completedBy
                                )
                        )
                );
    }

    /**
     * Redis HINCR 결과로 얻은 수정본 선택 횟수와 전체 반응 횟수를 함께 전달하기 위한
     * 불변 값 객체입니다.
     */
    private record CandidateCounts(long occurrenceCount, long totalCount) {}
}
