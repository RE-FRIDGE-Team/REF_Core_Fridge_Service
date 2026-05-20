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
 *   <li>{@code itemType} — Minor category의 item_type (응답의 {@code category}로 노출)</li>
 *   <li>{@code defaultUnit} — 최종 차감 단위 코드 (REFUsageUnit BASE 단위, nullable)</li>
 *   <li>{@code minPortionAmount}, {@code maxPortionAmount} — 1회 합리적 사용 범위 (nullable)</li>
 *   <li>{@code pieceWeightGram} — 1개당 g (nullable)</li>
 *   <li>{@code allowedUsageUnitsCsv} — 입력 가능 단위 CSV (nullable)</li>
 *   <li>{@code defaultUsageUnit} — 기본 활성 단위 코드 (nullable)</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Builder
public record REFGroceryItemMetadataDTO(
        Long id,
        String name,
        REFInventoryItemType itemType,
        String defaultUnit,
        Integer minPortionAmount,
        Integer maxPortionAmount,
        Integer pieceWeightGram,
        String allowedUsageUnitsCsv,
        String defaultUsageUnit
) {
}