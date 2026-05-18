package com.refridge.core_server.groceryItem.application.dto.query;

import lombok.Builder;

/**
 * GroceryItem 메타데이터(스푼 환산, 단위 등) 조회용 쿼리 DTO입니다.
 * fridge_server에 노출할 응답을 만들기 위해 사용됩니다.
 *
 * @author 이승훈
 * @since 2026. 5. 7.
 */
@Builder
public record REFGroceryItemMetadataQuery(
        Long groceryItemId
) {
}