package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카테고리가 변경된 부정 피드백을 처리합니다.
 * <p>
 * 카테고리 변경은 GroceryItem의 분류 자체를 바꾸는 것이므로
 * 영향 범위가 넓어 자동 반영하지 않고 관리자 검수 큐에 적재합니다.
 * <p>
 * 식재료명 동시 변경 여부에 따라 의미가 달라집니다:
 * <ul>
 *   <li>카테고리만 변경: 식재료는 맞지만 분류가 잘못됨</li>
 *   <li>식재료+카테고리 동시 변경: 완전히 다른 식재료로 인식된 것 (더 심각)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFCategoryReassignmentHandler implements REFImprovementActionHandler {

    private final REFFeedbackReviewItemRepository reviewItemRepository;

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.CATEGORY;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String correctedCategory = event.correction().getCorrectedCategoryPath();
        if (correctedCategory == null || correctedCategory.isBlank()) return;

        String targetValue = buildTargetValue(event);

        String context = String.format(
                "원본카테고리='%s', 수정카테고리='%s', 식재료변경=%s, 원본식재료='%s', completedBy='%s'",
                event.snapshot().getCategoryPath(),
                correctedCategory,
                event.diff().isGroceryItemChanged(),
                event.snapshot().getGroceryItemName(),
                event.snapshot().getCompletedBy()
        );

        reviewItemRepository.findByReviewTypeAndTargetValue(
                        REFReviewType.CATEGORY_REASSIGNMENT, targetValue)
                .ifPresentOrElse(
                        REFFeedbackReviewItem::incrementOccurrence,
                        () -> reviewItemRepository.save(
                                REFFeedbackReviewItem.create(
                                        REFReviewType.CATEGORY_REASSIGNMENT,
                                        targetValue,
                                        context,
                                        event.feedbackId().getValue()
                                )
                        )
                );

        log.info("[카테고리 재분류] 검수 큐 적재. '{}' → '{}', feedbackId={}",
                event.snapshot().getCategoryPath(), correctedCategory,
                event.feedbackId().getValue());
    }

    /** 식재료명 + 수정카테고리 조합으로 유니크 키 구성 */
    private String buildTargetValue(REFNegativeFeedbackEvent event) {
        String groceryItem = event.diff().isGroceryItemChanged()
                ? event.correction().getCorrectedGroceryItemName()
                : event.snapshot().getGroceryItemName();
        return groceryItem + "::" + event.correction().getCorrectedCategoryPath();
    }
}