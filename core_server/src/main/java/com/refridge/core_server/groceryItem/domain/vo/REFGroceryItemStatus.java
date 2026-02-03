package com.refridge.core_server.groceryItem.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum REFGroceryItemStatus {
    ACTIVE("A"),
    DELETED("D");

    private final String statusCode;

    public static REFGroceryItemStatus fromStatusCode(String statusCode) {
        return Arrays.stream(REFGroceryItemStatus.values())
                .filter(code -> code.statusCode.equals(statusCode))
                .findAny()
                .orElseThrow(RuntimeException::new);
    }
}
