package com.refridge.core_server.groceryItem.infra.persistence.dto;

import lombok.Builder;

@Builder
public record REFGroceryItemSummarizedDTO(
        Long groceryItemId,
        String groceryItemName,
        String representativeImageUrl
) {
}
