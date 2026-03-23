package com.refridge.core_server.recognition_feedback.infra.persistence.dto;

/**
 * 제품명 기준 피드백 집계 Projection DTO.
 * <p>
 * Product 자동 등록 판단 로직에서 사용됩니다.
 * QueryDSL GROUP BY + CASE WHEN 으로 단일 쿼리 조회.
 *
 * @param productName    정제된 제품명 (orig_product_name)
 * @param approvedCount  승인(긍정) 피드백 수
 * @param correctedCount 수정(부정) 피드백 수
 */
public record REFFeedbackAggregationDto(
        String productName,
        long approvedCount,
        long correctedCount
) {
    public long totalCount() {
        return approvedCount + correctedCount;
    }

    public double negativeRatio() {
        long total = totalCount();
        return total == 0 ? 0.0 : (double) correctedCount / total;
    }
}