package com.refridge.core_server.groceryItem.infra.persistence.dto;

import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;

/**
 * 식재료 ID → REFInventoryItemType 배치 조회용 DTO.
 * CSV 내보내기 시 카테고리태그 컬럼 채우는 용도.
 */
public record REFGroceryItemItemTypeDto(
        Long id,
        REFInventoryItemType itemType
) {}