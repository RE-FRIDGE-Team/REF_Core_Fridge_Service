package com.refridge.core_server.recognition_feedback.application.dto.result;

/**
 * 제품명 기준 피드백 집계 결과 DTO입니다.
 * <p>
 * 순수한 데이터 운반 목적의 DTO로, 집계값의 단순 파생값 계산만 포함합니다.
 * Product 등록 조건 판단과 같은 비즈니스 정책은
 * {@link com.refridge.core_server.recognition_feedback.domain.service.REFProductRegistrationPolicy}가
 * 담당합니다.
 *
 * @param productName    정제된 제품명 (orig_product_name)
 * @param approvedCount  승인(긍정) 피드백 수
 * @param correctedCount 수정(부정) 피드백 수
 */
public record REFFeedbackAggregationResult(
        String productName,
        long approvedCount,
        long correctedCount
) {
    /** 전체 피드백 수 (긍정 + 부정) */
    public long totalCount() {
        return approvedCount + correctedCount;
    }

    /** 부정 피드백 비율 (0.0 ~ 1.0) */
    public double negativeRatio() {
        long total = totalCount();
        return total == 0 ? 0.0 : (double) correctedCount / total;
    }
}