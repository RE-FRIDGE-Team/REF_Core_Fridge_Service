package com.refridge.core_server.bootstrap.dto;

public record REFCsvProductRowDto(
        String originalProductName,   // 원제품명
        String majorCategory,         // 대분류
        String subCategory,           // 중분류
        String inventoryItemType,     // 카테고리태그 (REFInventoryItemType)
        String groceryItemName,       // 식재료명
        String brandName              // 브랜드명
) {
}
