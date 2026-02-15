package com.refridge.core_server.groceryItem.application.dto.result;

/**
 * 제품명 검색 결과 DTO
 * 다른 Context에 노출하는 용도
 */
public record REFGroceryItemProductNameMatchResult(
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String imageUrl,
        String matchedRealProductName,
        double similarity
) {
}
