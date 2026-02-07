package com.refridge.core_server.groceryItem.application.dto;

import lombok.Builder;

@Builder
public record REFGroceryItemDeleteCommand(
        Long groceryItemId
) {
}
