package com.refridge.core_server.groceryItem.application.dto.result;

public record REFGroceryItemDetailInfoResult (
    Long groceryItemId,
    Long majorCategoryId,
    Long minorCategoryId,
    String groceryItemName,
    String categoryFullNameWithSeparator,
    String representativeImageUrl,
    String groceryItemClassificationCode,
    String categoryLabelColorCode
) {
}
