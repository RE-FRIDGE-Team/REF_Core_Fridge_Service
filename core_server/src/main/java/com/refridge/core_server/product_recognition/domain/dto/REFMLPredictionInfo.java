package com.refridge.core_server.product_recognition.domain.dto;

/**
 * 4번 분기: ML 모델 예측 결과
 */
public record REFMLPredictionInfo(
        String predictedGroceryItemName,
        double confidence
) {
}
