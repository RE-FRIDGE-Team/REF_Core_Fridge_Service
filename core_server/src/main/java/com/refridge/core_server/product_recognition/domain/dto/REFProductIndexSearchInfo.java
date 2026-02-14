package com.refridge.core_server.product_recognition.domain.dto;

/**
 * 3번 분기: 제품명 색인 검색 결과
 * GroceryItem Context의 제품명(alias) prefix 검색 결과
 */
public record REFProductIndexSearchInfo (
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String imageUrl
) {
}
