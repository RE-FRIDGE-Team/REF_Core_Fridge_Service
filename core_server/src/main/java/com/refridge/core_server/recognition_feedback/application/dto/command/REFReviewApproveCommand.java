package com.refridge.core_server.recognition_feedback.application.dto.command;

import lombok.Builder;

/**
 * 관리자 검수 항목 승인 커맨드.
 *
 * <h3>origProductName / origBrandName 용도</h3>
 * <p>
 * {@link com.refridge.core_server.recognition_feedback.domain.review.REFReviewType#CATEGORY_REASSIGNMENT}
 * 유형 승인 시 Product BC 핸들러({@code REFCategoryChangeOnApprovalEventHandler})가
 * 해당 제품이 이미 Product로 등록되어 있는지 확인하는 데 사용합니다.
 * 다른 유형에서는 null로 전달해도 무방합니다.
 * </p>
 *
 * @param reviewId        승인할 검수 항목 ID
 * @param adminNote       관리자 메모 (nullable)
 * @param origProductName 원본 인식 제품명 (카테고리 재분류 승인 시 사용, nullable)
 * @param origBrandName   원본 브랜드명 (카테고리 재분류 승인 시 신규 Product 생성에 사용, nullable)
 */
@Builder
public record REFReviewApproveCommand(
        Long reviewId,
        String adminNote,
        String origProductName,
        String origBrandName
) {
}
