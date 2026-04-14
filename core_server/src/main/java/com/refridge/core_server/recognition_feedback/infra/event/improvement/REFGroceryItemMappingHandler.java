package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
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
 * <h3>변경 사항 (2026. 4. 14.)</h3>
 * <p>
 * {@code evaluateAndConfirmCorrection()}에 {@code originalProductName}을 추가로 전달합니다.
 * 이를 통해 Product BC가 실제 제품명으로 Product를 upsert하여
 * 다음 인식 시 {@code ProductIndexSearch}에서 정확히 매칭될 수 있습니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 8. (수정: 2026. 4. 14.)
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
        if (event.diff().isRejectedButFood()) {
            log.debug("[식재료명 교정] REJECTED_BUT_FOOD 동시 트리거 — ExclusionRemovalHandler가 이미 처리. " +
                    "feedbackId={}", event.feedbackId().getValue());
            return;
        }

        String originalGroceryItem = event.snapshot().getGroceryItemName();
        String correctedGroceryItem = event.correction().getCorrectedGroceryItemName();
        String completedBy = event.snapshot().getCompletedBy();

        // 실제 제품명 — Product upsert 시 productName으로 사용
        String originalProductName = event.snapshot().getProductName();

        if (correctedGroceryItem == null || correctedGroceryItem.isBlank()) return;
        if (correctedGroceryItem.equals(originalGroceryItem)) return;

        String hashKey = REFGroceryItemCorrectionService.CORRECTION_CANDIDATE_PREFIX
                + originalGroceryItem;

        // Step 1: Redis Hash 카운터 증가
        CandidateCounts counts = incrementCandidateCounts(hashKey, correctedGroceryItem);
        if (counts == null) return;

        // Step 2: TTL 만료 후 재진입이면 DB에서 Hash 복원
        counts = restoreFromDbIfMiss(hashKey, originalGroceryItem, correctedGroceryItem, counts);

        // Step 3: TTL 갱신
        redisTemplate.expire(hashKey, CANDIDATE_TTL);

        log.info("[식재료명 교정] '{}' → '{}', 제품명='{}', 이 후보={}회, 전체={}회, feedbackId={}",
                originalGroceryItem, correctedGroceryItem, originalProductName,
                counts.occurrenceCount(), counts.totalCount(),
                event.feedbackId().getValue());

        // Step 4: 신규 식재료 가능성 검수 큐 적재
        enqueueForReview(event, correctedGroceryItem, completedBy);

        // Step 5: 3중 게이트 검사 및 교정 확정/reopen
        // originalProductName 전달 — confirmCorrection → 이벤트 → Product upsert 시 사용
        evaluateAndConfirmCorrection(
                hashKey, originalGroceryItem, correctedGroceryItem,
                counts, originalProductName);
    }

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

    private CandidateCounts restoreFromDbIfMiss(String hashKey,
                                                String originalGroceryItem,
                                                String correctedGroceryItem,
                                                CandidateCounts counts) {
        if (counts.occurrenceCount() != 1) return counts;

        List<REFFeedbackGroceryItemMappingCountDto> dbCounts =
                feedbackRepository.findGroceryItemMappingCountsByOriginalName(originalGroceryItem);

        if (dbCounts.isEmpty()) return counts;

        long dbTotal = 0L;
        for (REFFeedbackGroceryItemMappingCountDto dto : dbCounts) {
            redisTemplate.opsForHash().put(
                    hashKey, dto.correctedGroceryItemName(),
                    String.valueOf(dto.selectionCount()));
            dbTotal += dto.selectionCount();
        }

        long approvedCount = feedbackRepository
                .countApprovedByOriginalGroceryItemName(originalGroceryItem);
        long restoredTotal = dbTotal + approvedCount;

        redisTemplate.opsForHash().put(
                hashKey,
                REFGroceryItemCorrectionService.TOTAL_FIELD,
                String.valueOf(restoredTotal));

        long restoredOccurrence = dbCounts.stream()
                .filter(d -> correctedGroceryItem.equals(d.correctedGroceryItemName()))
                .mapToLong(REFFeedbackGroceryItemMappingCountDto::selectionCount)
                .findFirst()
                .orElse(1L);

        log.info("[식재료명 교정] Redis 복원. originalGroceryItem='{}', 후보수={}, " +
                        "correctedTotal={}, approvedCount={}, restoredTotal={}",
                originalGroceryItem, dbCounts.size(), dbTotal, approvedCount, restoredTotal);

        return new CandidateCounts(restoredOccurrence, restoredTotal);
    }

    /**
     * 3중 게이트를 검사하여 교정 확정 또는 reopen을 처리합니다.
     *
     * @param originalProductName 원본 제품명 — confirmCorrection에 전달하여
     *                            Product BC upsert 시 실제 제품명을 productName으로 사용하게 함
     */
    private void evaluateAndConfirmCorrection(String hashKey,
                                              String originalGroceryItem,
                                              String correctedGroceryItem,
                                              CandidateCounts counts,
                                              String originalProductName) {
        if (counts.occurrenceCount() < REFGroceryItemCorrectionService.MIN_CORRECTION_COUNT) {
            return;
        }

        Map<String, Long> allCounts = correctionService.getAllCandidateCounts(hashKey);

        boolean meetsThreshold = correctionService.meetsConfirmationThreshold(
                correctedGroceryItem, counts.occurrenceCount(),
                counts.totalCount(), allCounts);

        if (meetsThreshold) {
            correctionService.confirmCorrection(
                    originalGroceryItem,
                    correctedGroceryItem,
                    counts.occurrenceCount(),
                    counts.totalCount(),
                    originalProductName);   // 추가

        } else if (correctionService.isConfirmed(originalGroceryItem)) {
            correctionService.reopenCorrection(originalGroceryItem);
            log.info("[식재료명 교정] 경쟁 후보 재부상, reopen. originalGroceryItem='{}'",
                    originalGroceryItem);
        }
    }

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