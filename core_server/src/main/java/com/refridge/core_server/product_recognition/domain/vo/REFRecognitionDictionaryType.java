package com.refridge.core_server.product_recognition.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum REFRecognitionDictionaryType {

    EXCLUSION("EX", "비식재료 필터 사전", "recognition:dict:exclusion"),
    GROCERY_ITEM("GR", "식재료 매칭 사전","recognition:dict:grocery-item"),
    BRAND("BR", "브랜드명 매칭 사전", "recognition:dict:brand");

    private final String dbCode;
    private final String korDictName;
    private final String redisKey;

    public static REFRecognitionDictionaryType fromDbCode(String dbData) {
        return Arrays.stream(REFRecognitionDictionaryType.values())
                .filter(v -> v.dbCode.equals(dbData))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Invalid dbCode for REFRecognitionDictionaryType: " + dbData));
    }
}
