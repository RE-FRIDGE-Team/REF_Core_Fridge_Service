package com.refridge.core_server.groceryItem.application.dto;

import lombok.Builder;

@Builder
public record REFGroceryItemGetSummaryInfoCommand(
        Long groceryItemId
) {
}
