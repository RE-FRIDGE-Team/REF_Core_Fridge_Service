package com.refridge.core_server.groceryItem.application.dto;

import lombok.Builder;

@Builder
public record REFGroceryImageUpdateCommand(
        Long groceryItemId,
        String representativeImageUrl
) {
}
