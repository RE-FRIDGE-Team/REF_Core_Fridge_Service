package com.refridge.core_server.groceryItem.application.dto.query;

import lombok.Builder;

@Builder
public record REFGroceryItemSummaryInfoQuery(
        Long groceryItemId
) {
}
