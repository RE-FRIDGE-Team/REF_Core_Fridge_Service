package com.refridge.core_server.groceryItem.infra.persistence.dto;

import lombok.Builder;

@Builder
public record REFGroceryItemDetailDTO (
        Long groceryItemId,
        String groceryItemName,
        Long majorCategoryId,
        Long minorCategoryId,
        String categoryFullNameWithSeparator,
        String representativeImageUrl,
        String groceryItemClassificationCode
) {
}
