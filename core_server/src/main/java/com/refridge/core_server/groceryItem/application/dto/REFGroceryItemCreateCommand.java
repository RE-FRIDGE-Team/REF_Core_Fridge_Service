package com.refridge.core_server.groceryItem.application.dto;

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;
import lombok.Builder;

@Builder
public record REFGroceryItemCreateCommand(
        String groceryItemName,
        String representativeImageUrl,
        String groceryItemClassification,
        Long majorCategoryId, Long minorCategoryId
) {
    public REFGroceryItem toEntity(REFGroceryItemCategoryValidatorService categoryValidator) {
        return REFGroceryItem.create(
                groceryItemName,
                representativeImageUrl,
                groceryItemClassification,
                majorCategoryId,
                minorCategoryId,
                categoryValidator
        );
    }
}
