package com.refridge.core_server.product_recognition.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum REFRecognitionProcessingPath {
    /* 아직 인식 결과 도출 전 */
    WAITING("WT", "처리 대기 상태"),

    /* 비식재료 사전에 의해 필터링 됨 */
    EXCLUSION("EX", "비식재료 사전 필터링"),

    /* 식재료 사전 필터에 매칭된 경우 */
    INGREDIENT_DICT("ID", "식재료 사전 매칭"),

    /* DB 내의 제품명 목록 중 로직에 따라 매칭되는 제품명이 있는 경우 */
    PRODUCT_INDEX("PI", "제품명 목록 매칭"),

    /* 기존 분기에 모두 처리되지 않고 ML 모델에 의해 도출된 경우 */
    ML_MODEL("ML", "머신러닝 모델 결과 도출");


    private final String dbCode;
    private final String korCode;

    public static REFRecognitionProcessingPath fromDbCode(String dbCode){
        return Arrays.stream(REFRecognitionProcessingPath.values())
                .filter(p -> p.getDbCode().equals(dbCode))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Invalid dbCode for REFRecognitionProcessingPath: " + dbCode));
    }
}
