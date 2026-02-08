package com.refridge.core_server.groceryItem.application.dto.command;

import lombok.Builder;

@Builder
public record REFGroceryItemCategoryChangeCommand (
        Long groceryItemId,
        Long majorCategoryId,
        Long minorCategoryId
) {
}
