package com.refridge.core_server.groceryItem.application.dto.query;

import lombok.Builder;

/**
 * GroceryItem 메타데이터 조회용 쿼리 DTO.
 *
 * @author 이승훈
 * @since 2026. 5. 7.
 */
@Builder
public record REFGroceryItemMetadataQuery(
        Long groceryItemId
) {
}