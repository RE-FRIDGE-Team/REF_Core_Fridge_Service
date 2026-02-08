package com.refridge.core_server.groceryItem.application.dto.result;

import lombok.Builder;

@Builder
public record REFGroceryItemSummaryInfoResult (
        Long groceryItemId,
        String groceryItemName,
        String representativeImageUrl
) {
}
