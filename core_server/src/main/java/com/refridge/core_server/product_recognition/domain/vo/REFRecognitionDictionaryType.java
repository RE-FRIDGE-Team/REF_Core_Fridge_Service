package com.refridge.core_server.product_recognition.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum REFRecognitionDictionaryType {

    EXCLUSION("EX", "비식재료 사전", "recognition:dict:exclusion"),
    GROCERY_ITEM("GR", "식재료 사전","recognition:dict:grocery-item");

    private final String dbCode;
    private final String korCode;
    private final String redisKey;

    public static REFRecognitionDictionaryType fromDbCode(String dbData) {
        return Arrays.stream(REFRecognitionDictionaryType.values())
                .filter(v -> v.dbCode.equals(dbData))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Invalid dbCode for REFRecognitionDictionaryType: " + dbData));
    }
}
