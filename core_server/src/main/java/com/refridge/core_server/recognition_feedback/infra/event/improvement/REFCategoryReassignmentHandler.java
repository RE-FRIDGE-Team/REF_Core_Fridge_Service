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
 *
 * <h3>검수 큐 적재 이유</h3>
 * <p>
 * 카테고리 변경은 GroceryItem의 분류 자체를 바꾸는 것이므로
 * 영향 범위가 넓어 자동 반영하지 않고 관리자 검수 큐에 적재합니다.
 * 관리자가 승인하면 {@code REFCategoryReassignmentApprovedEvent}가 발행되어
 * {@code REFCategoryChangeOnApprovalEventHandler}가 GroceryItem 및 Product의 카테고리를 갱신합니다.
 * </p>
 *
 * <h3>targetValue 형식</h3>
 * <pre>
 *   "{correctedGroceryItemName}::{correctedCategoryPath}"
 *   예: "두부::채소류 > 두부/묵류"
 * </pre>
 * <p>
 * 이 형식은 {@code REFCategoryChangeOnApprovalEventHandler}가 파싱하여 처리합니다.
 * </p>
 *
 * <h3>식재료명 동시 변경 여부</h3>
 * <ul>
 *   <li>카테고리만 변경: 식재료는 맞지만 분류가 잘못됨 — targetValue에 원본 식재료명 사용</li>
 *   <li>식재료+카테고리 동시 변경: 완전히 다른 식재료로 인식 — targetValue에 수정 식재료명 사용</li>
 * </ul>
 *
 * <h3>sourceHandlerName 기록</h3>
 * <p>
 * 카테고리 재분류 유형에서도 {@code sourceHandlerName}을 기록합니다.
 * 현재는 승인 분기에 영향을 주지 않으나, 향후 분석/통계 목적으로 활용 가능합니다.
 * </p>
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
        String completedBy = event.snapshot().getCompletedBy();

        String context = String.format(
                "원본카테고리='%s', 수정카테고리='%s', 식재료변경=%s, " +
                        "원본식재료='%s', 원본제품명='%s', completedBy='%s'",
                event.snapshot().getCategoryPath(),
                correctedCategory,
                event.diff().isGroceryItemChanged(),
                event.snapshot().getGroceryItemName(),
                event.snapshot().getProductName(),
                completedBy
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
                                        event.feedbackId().getValue(),
                                        completedBy   // sourceHandlerName 기록
                                )
                        )
                );

        log.info("[카테고리 재분류] 검수 큐 적재. targetValue='{}', completedBy={}, feedbackId={}",
                targetValue, completedBy, event.feedbackId().getValue());
    }

    /**
     * 검수 항목의 유니크 키({@code targetValue})를 구성합니다.
     * <p>
     * 형식: "{correctedGroceryItemName}::{correctedCategoryPath}"
     * 동시에 식재료명이 변경된 경우 수정된 식재료명을 사용합니다.
     * 이 값이 {@code REFCategoryChangeOnApprovalEventHandler}에서 파싱됩니다.
     */
    private String buildTargetValue(REFNegativeFeedbackEvent event) {
        String groceryItem = event.diff().isGroceryItemChanged()
                ? event.correction().getCorrectedGroceryItemName()
                : event.snapshot().getGroceryItemName();
        return groceryItem + "::" + event.correction().getCorrectedCategoryPath();
    }
}
