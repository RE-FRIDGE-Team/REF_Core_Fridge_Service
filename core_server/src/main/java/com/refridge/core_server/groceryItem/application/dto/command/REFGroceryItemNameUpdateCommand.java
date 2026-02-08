package com.refridge.core_server.groceryItem.application.dto.command;

import lombok.Builder;

@Builder
public record REFGroceryItemNameUpdateCommand(
        Long groceryItemId,
        String name
) {
}
