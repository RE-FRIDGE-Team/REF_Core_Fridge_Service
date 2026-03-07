package com.refridge.core_server.groceryItem.infra.persistence.dto;

public record REFGroceryItemForUpsertDto(
        Long groceryItemId,
        Long majorCategoryId,
        Long minorCategoryId
) {
}
