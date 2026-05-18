package com.refridge.core_server.groceryItem.infra.persistence.dto;

import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import lombok.Builder;

/**
 * fridge_server에 노출할 GroceryItem 메타데이터 조회 결과 DTO입니다.
 *
 * <h3>구성</h3>
 * <ul>
 *   <li>{@code id} — Grocery item ID</li>
 *   <li>{@code name} — 식재료명</li>
 *   <li>{@code itemType} — Minor category의 item_type (RAW_MEAT, SAUCE_SEASONING 등)</li>
 *   <li>{@code defaultUnit} — 기본 사용량 단위 코드 (ML/G/EA, nullable)</li>
 *   <li>{@code minPortionAmount}, {@code maxPortionAmount} — 1회 분량 범위 (nullable)</li>
 *   <li>{@code spoonVolumeMl} — 1 TABLESPOON 부피 (nullable)</li>
 *   <li>{@code pieceWeightGram} — 1개당 중량 (nullable)</li>
 *   <li>{@code allowedUsageTypesCsv} — 노출 가능 UsageSpec CSV (nullable)</li>
 *   <li>{@code defaultUsageType} — 기본 활성 UsageSpec 코드 (nullable)</li>
 * </ul>
 *
 * <h3>itemType 결정 경로</h3>
 * <p>REFGroceryItem.minorCategoryId → REFMinorGroceryCategory.itemType (JOIN)</p>
 *
 * @author 이승훈
 * @since 2026. 5. 7. (확장: 2026. 5. 9.)
 */
@Builder
public record REFGroceryItemMetadataDTO(
        Long id,
        String name,
        REFInventoryItemType itemType,
        String defaultUnit,
        Integer minPortionAmount,
        Integer maxPortionAmount,
        Integer spoonVolumeMl,
        Integer pieceWeightGram,
        String allowedUsageTypesCsv,
        String defaultUsageType
) {
}