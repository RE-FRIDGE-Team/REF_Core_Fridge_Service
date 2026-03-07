package com.refridge.core_server.groceryItem.application.dto.result;

public record REFGroceryItemUpsertResult(
        Long groceryItemId,
        Long majorCategoryId,
        Long minorCategoryId,
        // true=신규생성, false=기존 반환
        boolean created
) {
}
