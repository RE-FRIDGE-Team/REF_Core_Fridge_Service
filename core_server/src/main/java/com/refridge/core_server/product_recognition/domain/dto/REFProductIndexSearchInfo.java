package com.refridge.core_server.product_recognition.domain.dto;

/**
 * 3번 분기: 제품명 색인 검색 결과
 * GroceryItem Context의 제품명(alias) prefix 검색 결과
 */
public record REFProductIndexSearchInfo (
        Long productId,
        String productName,
        String brandName,
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String imageUrl,
        MatchType matchType,
        double similarityScore
) {
    public enum MatchType {
        EXACT,           // 완전 일치
        BRAND_PRODUCT,   // 브랜드 + 제품명 일치
        PARTIAL,         // 부분 일치
        FUZZY            // 유사도 일치
    }

    public static REFProductIndexSearchInfo of(
            Long productId,
            String productName,
            String brandName,
            Long groceryItemId,
            String groceryItemName,
            String categoryPath,
            String imageUrl,
            MatchType matchType,
            double similarityScore
    ) {
        return new REFProductIndexSearchInfo(
                productId, productName, brandName,
                groceryItemId, groceryItemName,
                categoryPath, imageUrl,
                matchType, similarityScore
        );
    }
}
