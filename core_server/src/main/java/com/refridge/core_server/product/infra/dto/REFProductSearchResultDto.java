package com.refridge.core_server.product.infra.dto;

/**
 * 제품 검색 결과 DTO (Product Context)
 */
public record REFProductSearchResultDto(
        Long productId,
        String productName,
        String brandName,
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String representativeImageUrl
) {
}
