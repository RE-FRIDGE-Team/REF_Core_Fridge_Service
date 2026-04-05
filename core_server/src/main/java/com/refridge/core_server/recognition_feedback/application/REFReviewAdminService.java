package com.refridge.core_server.recognition_feedback.application;

import com.refridge.core_server.recognition_feedback.application.dto.command.REFReviewApproveCommand;
import com.refridge.core_server.recognition_feedback.application.dto.command.REFReviewRejectCommand;
import com.refridge.core_server.recognition_feedback.domain.event.REFCategoryReassignmentApprovedEvent;
import com.refridge.core_server.recognition_feedback.domain.review.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 검수 큐 승인/반려 처리 Application Service입니다.
 *
 * <h3>승인 로직 분기</h3>
 * <table border="1" cellpadding="6" style="border-collapse:collapse;">
 *   <thead>
 *     <tr><th>검수 유형</th><th>sourceHandlerName</th><th>처리 결과</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>CATEGORY_REASSIGNMENT</td>
 *       <td>모두</td>
 *       <td>APPROVED + REFCategoryReassignmentApprovedEvent 발행
 *           → REFCategoryChangeOnApprovalEventHandler(Async)가 GroceryItem/Product 카테고리 갱신</td>
 *     </tr>
 *     <tr>
 *       <td>NEW_GROCERY_ITEM</td>
 *       <td>MLPrediction</td>
 *       <td>ML_TRAINING_PENDING (Phase 2: CSV 내보내기 후 최종 APPROVED 전환)</td>
 *     </tr>
 *     <tr>
 *       <td>NEW_GROCERY_ITEM</td>
 *       <td>기타</td>
 *       <td>APPROVED</td>
 *     </tr>
 *     <tr>
 *       <td>EXCLUSION_REMOVAL</td>
 *       <td>모두</td>
 *       <td>APPROVED (비식재료 사전 키워드 제거는 별도 배치로 처리 예정)</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>인증/인가</h3>
 * <p>
 * 현재는 인증 없이 누구나 호출 가능합니다.
 * 추후 JWT 토큰 기반 ADMIN 역할 검증으로 보완 예정입니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFCategoryChangeOnApprovalEventHandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFReviewAdminService {

    private final REFFeedbackReviewItemRepository reviewItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 검수 항목을 승인합니다.
     *
     * <h3>처리 흐름</h3>
     * <ol>
     *   <li>검수 항목 조회 (없으면 IllegalArgumentException)</li>
     *   <li>검수 유형 + sourceHandlerName 기준으로 승인 방식 분기</li>
     *   <li>카테고리 재분류 승인 시 REFCategoryReassignmentApprovedEvent 발행</li>
     * </ol>
     *
     * @param command 승인 커맨드
     * @throws IllegalArgumentException 검수 항목을 찾을 수 없는 경우
     * @throws IllegalStateException    PENDING 상태가 아닌 경우
     */
    @Transactional
    public void approveReviewItem(REFReviewApproveCommand command) {
        REFFeedbackReviewItem item = findItemOrThrow(command.reviewId());

        if (item.getReviewType() == REFReviewType.NEW_GROCERY_ITEM
                && item.isFromMlPrediction()) {
            // MLPrediction 기반 신규 식재료 → ML 학습 데이터로 수집 후 최종 승인
            item.approveForMlTraining(command.adminNote());
            log.info("[관리자 승인] ML 학습 대기 전환. reviewId={}, target='{}'",
                    command.reviewId(), item.getTargetValue());

        } else {
            // 일반 승인
            item.approve(command.adminNote());
            log.info("[관리자 승인] 승인 완료. reviewId={}, type={}, target='{}'",
                    command.reviewId(), item.getReviewType(), item.getTargetValue());
        }

        reviewItemRepository.save(item);

        // 카테고리 재분류 승인 → 비동기 카테고리 변경 이벤트 발행
        if (item.getReviewType() == REFReviewType.CATEGORY_REASSIGNMENT) {
            eventPublisher.publishEvent(new REFCategoryReassignmentApprovedEvent(
                    item.getId(),
                    item.getTargetValue(),
                    command.origProductName(),
                    command.origBrandName(),
                    item.getSourceFeedbackId()
            ));
            log.info("[관리자 승인] 카테고리 재분류 이벤트 발행. reviewId={}", command.reviewId());
        }
    }

    /**
     * 검수 항목을 반려합니다.
     *
     * @param command 반려 커맨드
     * @throws IllegalArgumentException 검수 항목을 찾을 수 없는 경우
     * @throws IllegalStateException    PENDING 상태가 아닌 경우
     */
    @Transactional
    public void rejectReviewItem(REFReviewRejectCommand command) {
        REFFeedbackReviewItem item = findItemOrThrow(command.reviewId());
        item.reject(command.adminNote());
        reviewItemRepository.save(item);

        log.info("[관리자 반려] 반려 완료. reviewId={}, type={}, target='{}', 사유='{}'",
                command.reviewId(), item.getReviewType(), item.getTargetValue(),
                command.adminNote());
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    private REFFeedbackReviewItem findItemOrThrow(Long reviewId) {
        return reviewItemRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "검수 항목을 찾을 수 없습니다: reviewId=" + reviewId));
    }
}
