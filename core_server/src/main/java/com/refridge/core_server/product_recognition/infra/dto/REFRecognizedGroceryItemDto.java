package com.refridge.core_server.product_recognition.infra.dto;

/**
 * 인식된 식재료 정보 DTO.
 * ProductRecognition Context에서 사용하는 GroceryItem 정보.
 */
public record REFRecognizedGroceryItemDto(
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String representativeImageUrl
) {
    public static REFRecognizedGroceryItemDto of(
            Long groceryItemId,
            String groceryItemName,
            String categoryPath,
            String representativeImageUrl
    ) {
        return new REFRecognizedGroceryItemDto(
                groceryItemId,
                groceryItemName,
                categoryPath,
                representativeImageUrl
        );
    }
}