package com.refridge.core_server.groceryItem.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum REFGroceryItemClassification {
    FOOD_INGREDIENTS("F", "식재료"),
    RETORT_POUCH("R", "레트로트 제품"),
    MEAL_KIT("M", "밀키트");

    private final String typeCode;
    private final String korCode;

    public static REFGroceryItemClassification fromTypeCode(String typeCode) {
        return Arrays.stream(REFGroceryItemClassification.values())
                .filter(type -> type.getTypeCode().equals(typeCode))
                .findAny()
                .orElseThrow(RuntimeException::new);
    }
}
