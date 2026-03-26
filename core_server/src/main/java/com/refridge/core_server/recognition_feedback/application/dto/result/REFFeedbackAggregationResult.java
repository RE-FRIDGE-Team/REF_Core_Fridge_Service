package com.refridge.core_server.recognition_feedback.application.dto.result;

/**
 * 제품명 기준 피드백 집계 결과.
 * Product 자동 등록 판단 및 핸들러 품질 메트릭에 활용됩니다.
 */
public record REFFeedbackAggregationResult(
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

    /**
     * 지정된 임계값 조건을 만족하는지 판단합니다.
     *
     * @param minPositive     최소 긍정 피드백 수
     * @param maxNegativeRatio 최대 부정 비율 (0.0 ~ 1.0)
     * @return 조건 충족 여부
     */
    public boolean meetsAutoRegistrationThreshold(int minPositive, double maxNegativeRatio) {
        return approvedCount >= minPositive && negativeRatio() < maxNegativeRatio;
    }
}