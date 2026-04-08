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
 * <h3>핵심 역할 — GroceryItem 매핑 투표 집계 및 자동 확정</h3>
 * <p>
 * {@link REFProductNameAliasHandler}가 제품명 alias를 관리하듯,
 * 이 핸들러는 "파이프라인 인식 식재료명 → 사용자 수정 식재료명" 매핑을
 * Redis Hash에 누적하고 3중 게이트 조건 충족 시 자동 확정합니다.
 * </p>
 *
 * <h3>전체 처리 흐름</h3>
 * <pre>
 * REFNegativeFeedbackDispatcher
 *   │
 *   └─▶ handle(event)
 *         │
 *         ├─ incrementCandidateCounts()
 *         │     ├─ Redis HINCR: correctedGroceryItemName 선택 횟수 +1
 *         │     └─ Redis HINCR: __total__ +1
 *         │
 *         ├─ restoreFromDbIfMiss()
 *         │     └─ occurrenceCount == 1?
 *         │           ├─ Case A (첫 피드백): DB 비어있음 → 그대로 진행
 *         │           └─ Case B (TTL 만료):  DB 이력 존재 → Hash 전체 복원
 *         │
 *         ├─ TTL 갱신 (30일)
 *         │
 *         ├─ enqueueForReview() — 신규 식재료 가능성 검수 큐 적재
 *         │
 *         └─ evaluateAndConfirmMapping()
 *               ├─ Gate 1: occurrenceCount < MIN_MAPPING_COUNT(10) → 리턴
 *               ├─ Gate 2: occurrenceCount / __total__ >= 0.7
 *               ├─ Gate 3: occurrenceCount / 2위 >= 3.0
 *               │
 *               ├─ 통과 → confirmMapping()   [Phase 2에서 DB 저장 추가]
 *               └─ 미달 + CONFIRMED → reopenMapping()
 * </pre>
 *
 * <h3>Redis 데이터 구조</h3>
 * <pre>
 *   Hash: feedback:grocery-item-mapping:{originalGroceryItemName}
 *     Field: {correctedName1} → 선택 횟수
 *     Field: __total__        → 전체 반응 횟수 (긍정 포함)
 *     TTL: 30일
 *
 *   Hash: grocery-item-mapping:confirmed
 *     Field: {originalName} → {correctedName}
 *     TTL: 없음
 * </pre>
 *
 * <h3>__total__ 관리</h3>
 * <p>
 * 부정 피드백(이 핸들러)에서 수정본 선택 시 +1,
 * 긍정 피드백({@link REFPositiveFeedbackAggregationHandler})에서 승인 시 +1.
 * Gate 2 분모로 사용되어 다수 승인 시 소수 악용 수정본이 확정되지 않도록 방어.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 7.
 * @see REFGroceryItemMappingConfirmationService
 * @see REFPositiveFeedbackAggregationHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemMappingHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFFeedbackReviewItemRepository reviewItemRepository;
    private final REFGroceryItemMappingConfirmationService mappingConfirmationService;
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

        // 동일한 값이면 처리 불필요
        if (correctedGroceryItem.equals(originalGroceryItem)) return;

        String hashKey = REFGroceryItemMappingConfirmationService.MAPPING_CANDIDATE_PREFIX
                + originalGroceryItem;

        // Step 1: Redis Hash 카운터 증가
        CandidateCounts counts = incrementCandidateCounts(hashKey, correctedGroceryItem);
        if (counts == null) return;

        // Step 2: TTL 만료 후 재진입이면 DB에서 Hash 복원
        counts = restoreFromDbIfMiss(hashKey, originalGroceryItem, correctedGroceryItem, counts);

        // Step 3: TTL 갱신
        redisTemplate.expire(hashKey, CANDIDATE_TTL);

        log.info("[GroceryItem 매핑] '{}' → '{}', 이 후보={}회, 전체={}회, feedbackId={}",
                originalGroceryItem, correctedGroceryItem,
                counts.occurrenceCount(), counts.totalCount(),
                event.feedbackId().getValue());

        // Step 4: 신규 식재료 가능성 검수 큐 적재 (기존 로직 유지)
        enqueueForReview(event, correctedGroceryItem, completedBy);

        // Step 5: 3중 게이트 검사 및 매핑 확정/reopen
        evaluateAndConfirmMapping(hashKey, originalGroceryItem, correctedGroceryItem, counts);
    }

    /**
     * Redis Hash에 수정본 선택 횟수와 전체 반응 횟수를 각각 1씩 증가시킵니다.
     */
    private CandidateCounts incrementCandidateCounts(String hashKey, String correctedGroceryItem) {
        Long occurrenceCount = redisTemplate.opsForHash()
                .increment(hashKey, correctedGroceryItem, 1);
        Long totalCount = redisTemplate.opsForHash()
                .increment(hashKey, REFGroceryItemMappingConfirmationService.TOTAL_FIELD, 1);

        if (occurrenceCount == null || totalCount == null) {
            log.warn("[GroceryItem 매핑] Redis increment 실패. hashKey='{}'", hashKey);
            return null;
        }

        return new CandidateCounts(occurrenceCount, totalCount);
    }

    /**
     * TTL 만료 후 첫 접근인 경우 DB에서 매핑 이력을 조회하여 Hash를 복원합니다.
     * REFProductNameAliasHandler.restoreFromDbIfMiss()와 동일한 전략.
     */
    private CandidateCounts restoreFromDbIfMiss(String hashKey,
                                                String originalGroceryItem,
                                                String correctedGroceryItem,
                                                CandidateCounts counts) {
        if (counts.occurrenceCount() != 1) return counts;

        List<REFFeedbackGroceryItemMappingCountDto> dbCounts =
                feedbackRepository.findGroceryItemMappingCountsByOriginalName(originalGroceryItem);

        if (dbCounts.isEmpty()) return counts; // Case A: 진짜 첫 피드백

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

        redisTemplate.opsForHash().put(
                hashKey,
                REFGroceryItemMappingConfirmationService.TOTAL_FIELD,
                String.valueOf(dbTotal)
        );

        long restoredOccurrence = dbCounts.stream()
                .filter(d -> correctedGroceryItem.equals(d.correctedGroceryItemName()))
                .mapToLong(REFFeedbackGroceryItemMappingCountDto::selectionCount)
                .findFirst()
                .orElse(1L);

        log.info("[GroceryItem 매핑] Redis 복원. originalGroceryItem='{}', 후보수={}, total={}",
                originalGroceryItem, dbCounts.size(), dbTotal);

        return new CandidateCounts(restoredOccurrence, dbTotal);
    }

    /**
     * 3중 게이트를 검사하여 매핑 확정 또는 reopen을 처리합니다.
     */
    private void evaluateAndConfirmMapping(String hashKey,
                                           String originalGroceryItem,
                                           String correctedGroceryItem,
                                           CandidateCounts counts) {
        // Gate 1 빠른 차단
        if (counts.occurrenceCount() < REFGroceryItemMappingConfirmationService.MIN_MAPPING_COUNT) {
            return;
        }

        Map<String, Long> allCounts =
                mappingConfirmationService.getAllCandidateCounts(hashKey);

        boolean meetsThreshold = mappingConfirmationService.meetsConfirmationThreshold(
                correctedGroceryItem,
                counts.occurrenceCount(),
                counts.totalCount(),
                allCounts
        );

        if (meetsThreshold) {
            // Phase 2에서 DB 저장 + GroceryItem 존재 분기 로직 추가 예정
            mappingConfirmationService.confirmMapping(originalGroceryItem, correctedGroceryItem);
            log.info("[GroceryItem 매핑] 확정. '{}' → '{}'",
                    originalGroceryItem, correctedGroceryItem);

        } else if (mappingConfirmationService.isConfirmed(originalGroceryItem)) {
            mappingConfirmationService.reopenMapping(originalGroceryItem);
            log.info("[GroceryItem 매핑] 경쟁 후보 재부상, reopen. originalGroceryItem='{}'",
                    originalGroceryItem);
        }
    }

    /**
     * 신규 식재료 후보를 검수 큐에 적재합니다.
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

    private record CandidateCounts(long occurrenceCount, long totalCount) {}
}