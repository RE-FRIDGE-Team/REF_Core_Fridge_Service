package com.refridge.core_server.bootstrap.dto;

import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;

/**
 * CSV 파일에서 읽어들인 제품 정보를 담는 DTO.<p>
 * {@link com.refridge.core_server.bootstrap.REFProductAndGroceryItemInitializer}에서 사용한다.
 * @param originalProductName 원제품명 (CSV 파일에 있는 제품명 그대로)
 * @param majorCategory 대분류
 * @param subCategory 중분류
 * @param inventoryItemType 카테고리 태그 {@link REFInventoryItemType}
 * @param groceryItemName 식재료명 (GroceryItem 이름으로 사용할 값)
 * @param brandName 브랜드명
 */
public record REFCsvProductRowDto(
        String originalProductName,
        String majorCategory,
        String subCategory,
        String inventoryItemType,
        String groceryItemName,
        String brandName
) {
}
