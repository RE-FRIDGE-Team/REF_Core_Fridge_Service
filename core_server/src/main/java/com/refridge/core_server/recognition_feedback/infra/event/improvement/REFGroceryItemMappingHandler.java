package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 식재료명이 변경된 부정 피드백을 처리합니다.
 *
 * <h3>completedBy 기반 처리 분기</h3>
 * <ul>
 *   <li>{@code GroceryItemDictMatch}: 사전 매칭이 잘못됨 → 매핑 보정 데이터 누적</li>
 *   <li>{@code ProductIndexSearch}: Product↔GroceryItem 매핑이 잘못됨 → 매핑 보정 데이터 누적</li>
 *   <li>{@code MLPrediction}: ML 모델 재학습 데이터로 수집 → 검수 항목에 {@code sourceHandlerName="MLPrediction"} 기록</li>
 * </ul>
 *
 * <h3>sourceHandlerName 기록 이유</h3>
 * <p>
 * {@code sourceHandlerName = "MLPrediction"}으로 기록된 검수 항목은
 * 관리자 승인 시 {@link com.refridge.core_server.recognition_feedback.domain.review.REFReviewStatus#ML_TRAINING_PENDING}
 * 상태로 전환됩니다. Phase 2에서 Spring Batch 잡이 이 항목들을 CSV로 내보내
 * ML 모델 재학습 데이터로 활용합니다.
 * </p>
 *
 * <h3>신규 식재료 가능성</h3>
 * <p>
 * 사용자가 입력한 식재료명이 DB에 없는 경우 신규 식재료 생성 검수 큐에 적재합니다.
 * 관리자 검토 후 승인/반려합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemMappingHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFFeedbackReviewItemRepository reviewItemRepository;

    private static final String MAPPING_COUNTER_PREFIX = "feedback:grocery-mapping:";

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.GROCERY_ITEM;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String originalGroceryItem = event.snapshot().getGroceryItemName();
        String correctedGroceryItem = event.correction().getCorrectedGroceryItemName();
        String completedBy = event.snapshot().getCompletedBy();
        boolean categoryAlsoChanged = event.diff().isCategoryChanged();

        if (correctedGroceryItem == null || correctedGroceryItem.isBlank()) return;

        // 매핑 보정 카운터 누적 (completedBy 기준으로 핸들러별 분류)
        String counterKey = MAPPING_COUNTER_PREFIX +
                buildMappingKey(originalGroceryItem, correctedGroceryItem);
        redisTemplate.opsForHash().increment(
                counterKey, completedBy != null ? completedBy : "UNKNOWN", 1);

        // 신규 식재료 가능성 → 검수 큐에 적재
        // sourceHandlerName을 함께 저장하여 MLPrediction 여부를 관리자 승인 시 판단
        enqueueForReview(event, correctedGroceryItem, completedBy, categoryAlsoChanged);

        log.info("[식재료 매핑 보정] '{}' → '{}', completedBy={}, categoryAlsoChanged={}, feedbackId={}",
                originalGroceryItem, correctedGroceryItem, completedBy,
                categoryAlsoChanged, event.feedbackId().getValue());
    }

    /**
     * 신규 식재료 후보를 검수 큐에 적재합니다.
     * <p>
     * 이미 동일한 {@code correctedGroceryItem}이 PENDING 상태로 존재하면
     * {@code occurrenceCount}만 증가시킵니다 (중복 적재 방지).
     * 신규 항목 생성 시 {@code sourceHandlerName}을 기록하여
     * 관리자 승인 로직이 MLPrediction 여부를 판단할 수 있게 합니다.
     */
    private void enqueueForReview(REFNegativeFeedbackEvent event,
                                  String correctedGroceryItem,
                                  String completedBy,
                                  boolean categoryAlsoChanged) {

        String context = String.format(
                "원본식재료='%s', 수정식재료='%s', completedBy='%s', " +
                        "카테고리동시변경=%s, 원본제품명='%s'",
                event.snapshot().getGroceryItemName(),
                correctedGroceryItem,
                completedBy,
                categoryAlsoChanged,
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
                                        completedBy   // sourceHandlerName: MLPrediction 여부 기록
                                )
                        )
                );
    }

    private String buildMappingKey(String original, String corrected) {
        return (original != null ? original : "NULL") + "::" + corrected;
    }
}
