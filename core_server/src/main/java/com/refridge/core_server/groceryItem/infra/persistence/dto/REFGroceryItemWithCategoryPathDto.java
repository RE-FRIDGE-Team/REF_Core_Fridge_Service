package com.refridge.core_server.groceryItem.infra.persistence.dto;

public record REFGroceryItemWithCategoryPathDto(
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String representativeImageUrl
) {
}
