package com.refridge.core_server.product_recognition.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum REFProductRecognitionStatus {
    /* 인식 진행 과정 */
    PENDING("P", "인식 진행 중"),

    /* PENDING -> 정상적으로 인식 완료된 경우 (어떤 플로우 차트 분기에서 인식을 하더라도) */
    COMPLETED("C", "인식 완료"),

    /* PENDING -> 비식재료 필터에 걸려 인식이 거절된 경우 */
    REJECTED("R", "인식 거절"),

    /* PENDING -> ML의 예측 결과가 매칭되는 GroceryItem이 없는 경우를 의미 */
    NO_MATCH("N", "일치하는 식재료 없음");

    private final String dbCode;
    private final String korCode;

    public static REFProductRecognitionStatus convertFromDbCode(String dbCode){
        return Arrays.stream(REFProductRecognitionStatus.values())
                .filter(status -> status.getDbCode().equals(dbCode))
                .findAny()
                .orElseThrow(RuntimeException::new);
    }
}
