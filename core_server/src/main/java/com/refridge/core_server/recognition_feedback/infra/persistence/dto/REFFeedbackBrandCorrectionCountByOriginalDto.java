package com.refridge.core_server.recognition_feedback.infra.persistence.dto;

/**
 * 원본 브랜드명 기준 교체 브랜드명별 선택 횟수 Projection DTO.
 *
 * <p>
 * 사용 시점:
 *   Redis 브랜드 교체 후보 Hash({@code feedback:brand-correction:{originalBrand}})가
 *   TTL 만료 후 첫 접근 시 DB에서 실제 누적 횟수를 복원할 때 사용합니다.
 * </p>
 *
 * <p>
 * 기존 {@link REFFeedbackBrandCorrectionCountDto}는 alias 후보(제품명 수정) 용도이므로
 * 브랜드 교체 전용 DTO를 별도로 정의합니다.
 * </p>
 *
 * @param originalBrandName    원본 인식 브랜드명 (스냅샷의 {@code orig_brand_name})
 * @param correctedBrandName   사용자가 교체 입력한 브랜드명
 * @param selectionCount       해당 교체 브랜드를 선택한 횟수
 */
public record REFFeedbackBrandCorrectionCountByOriginalDto(
        String originalBrandName,
        String correctedBrandName,
        long selectionCount
) {
}