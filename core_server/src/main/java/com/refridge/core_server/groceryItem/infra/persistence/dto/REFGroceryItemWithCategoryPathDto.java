package com.refridge.core_server.groceryItem.infra.persistence.dto;

public record REFGroceryItemWithCategoryPathDto(
        Long groceryItemId,
        String groceryItemName,
        Long majorCategoryId,
        Long minorCategoryId,
        String categoryPath,
        String representativeImageUrl
) {
}
