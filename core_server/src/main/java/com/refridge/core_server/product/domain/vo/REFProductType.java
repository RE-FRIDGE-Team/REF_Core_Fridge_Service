package com.refridge.core_server.product.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 제품 타입
 * INGREDIENT: 재료형 (비가공품, 브랜드 없지만 상세정보 있음)
 *   - 예: "돼지고기 목살 500g"
 * GENERIC: 일반 가공품 (브랜드 있음)
 *   - 예: "델몬트 토마토주스"
 * BRANDED: 브랜드 제품 (브랜드명이 제품 식별에 중요)
 *   - 예: "새우깡", "코카콜라"
 * SIMPLE: 단순 재료 (Virtual Product)
 *   - 예: "양파", "사과"
 */
@Getter
@AllArgsConstructor
public enum REFProductType {
    INGREDIENT("I", "재료형"),
    GENERIC("G", "일반가공품"),
    BRANDED("B", "브랜드제품"),
    SIMPLE("S", "단순재료");

    private final String code;
    private final String description;

    public static REFProductType fromCode(String code) {
        return Arrays.stream(REFProductType.values())
                .filter(type -> type.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "잘못된 제품 타입 코드입니다: " + code
                ));
    }

    public boolean isIngredient() {
        return this == INGREDIENT;
    }

    public boolean isGeneric() {
        return this == GENERIC;
    }

    public boolean isBranded() {
        return this == BRANDED;
    }

    public boolean isSimple() {
        return this == SIMPLE;
    }
}