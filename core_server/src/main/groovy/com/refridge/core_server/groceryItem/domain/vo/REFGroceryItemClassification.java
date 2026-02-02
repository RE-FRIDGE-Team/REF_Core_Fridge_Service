package com.refridge.core_server.groceryItem.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@AllArgsConstructor
public enum REFGroceryItemClassification {
    FOOD_INGREDIENTS("F"),
    RETORT_POUCH("R"),
    MEAL_KIT("M");

    @Getter
    private final String typeCode;

    public static REFGroceryItemClassification fromTypeCode(String typeCode) {
        return Arrays.stream(REFGroceryItemClassification.values())
                .filter(type -> type.getTypeCode().equals(typeCode))
                .findAny()
                .orElseThrow(RuntimeException::new);
    }
}
