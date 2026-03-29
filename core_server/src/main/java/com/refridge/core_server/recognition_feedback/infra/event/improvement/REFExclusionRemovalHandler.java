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
 * 비식재료로 반려되었으나 사용자가 식재료로 수정한 피드백을 처리합니다.
 * <p>
 * 비식재료 사전에서 키워드를 잘못 제거하면 실제 비식재료가 식재료로 인식될 수 있으므로
 * 자동 제거하지 않고 반드시 관리자 검수를 거칩니다.
 * <p>
 * 검수 큐 대상값: 반려 시 매칭된 비식재료 키워드 ({@code rejectionKeyword})
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFExclusionRemovalHandler implements REFImprovementActionHandler {

    private final REFFeedbackReviewItemRepository reviewItemRepository;

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.REJECTED_BUT_FOOD;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String rejectionKeyword = event.snapshot().getRejectionKeyword();
        if (rejectionKeyword == null || rejectionKeyword.isBlank()) {
            log.warn("[비식재료 사전] 반려 키워드 누락. feedbackId={}", event.feedbackId().getValue());
            return;
        }

        String context = String.format(
                "원본제품명='%s', 사용자수정식재료='%s', 반려키워드='%s'",
                event.snapshot().getProductName(),
                event.correction().getCorrectedGroceryItemName(),
                rejectionKeyword
        );

        reviewItemRepository.findByReviewTypeAndTargetValue(
                        REFReviewType.EXCLUSION_REMOVAL, rejectionKeyword)
                .ifPresentOrElse(
                        REFFeedbackReviewItem::incrementOccurrence,
                        () -> reviewItemRepository.save(
                                REFFeedbackReviewItem.create(
                                        REFReviewType.EXCLUSION_REMOVAL,
                                        rejectionKeyword,
                                        context,
                                        event.feedbackId().getValue()
                                )
                        )
                );

        log.info("[비식재료 사전] 키워드 제거 검수 요청. keyword='{}', feedbackId={}",
                rejectionKeyword, event.feedbackId().getValue());
    }
}