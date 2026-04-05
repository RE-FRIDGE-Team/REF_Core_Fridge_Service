package com.refridge.core_server.recognition_feedback.domain.event;

import java.util.UUID;

/**
 * 관리자가 카테고리 재분류 검수 항목을 승인했을 때 발행되는 도메인 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   관리자 승인 요청
 *     → REFReviewAdminService.approveReviewItem()
 *         → REFFeedbackReviewItem.approve()
 *         → REFCategoryReassignmentApprovedEvent 발행
 *             → REFCategoryChangeOnApprovalEventHandler (Async)
 *                   ① GroceryItem 카테고리 변경
 *                   ② Product 존재 → updateCategoryReference()
 *                      Product 없음  → upsertProduct() (다음 recognition 대비)
 * </pre>
 *
 * <h3>targetValue 형식</h3>
 * <pre>
 *   "{correctedGroceryItemName}::{correctedCategoryPath}"
 *   예: "두부::채소류 > 두부/묵류"
 * </pre>
 *
 * <h3>origProductName 용도</h3>
 * <p>
 * 핸들러가 Product BC에서 해당 원본 제품명으로 등록된 Product 존재 여부를 확인합니다.
 * Product가 존재하면 비정규화된 카테고리 참조를 업데이트하고,
 * 없으면 수정된 GroceryItem 매핑으로 신규 Product를 생성합니다.
 * 신규 Product를 미리 등록해두면 다음 인식 시 ProductIndexSearch 단계에서
 * GroceryItemDict/ML 이전에 정확하게 매칭되어 오분류를 예방합니다.
 * </p>
 *
 * @param reviewId         승인된 검수 항목 ID
 * @param targetValue      "{correctedGroceryItemName}::{correctedCategoryPath}" 형식
 * @param origProductName  원본 인식 제품명 (Product 존재 여부 확인용, nullable)
 * @param origBrandName    원본 브랜드명 (신규 Product 생성 시 사용, nullable)
 * @param sourceFeedbackId 최초 발생 피드백 ID
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see com.refridge.core_server.recognition_feedback.infra.event.REFCategoryChangeOnApprovalEventHandler
 */
public record REFCategoryReassignmentApprovedEvent(
        Long reviewId,
        String targetValue,
        String origProductName,
        String origBrandName,
        UUID sourceFeedbackId
) {
}
