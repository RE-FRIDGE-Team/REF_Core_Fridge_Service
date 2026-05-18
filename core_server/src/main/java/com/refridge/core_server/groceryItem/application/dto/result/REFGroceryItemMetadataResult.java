package com.refridge.core_server.groceryItem.application.dto.result;

import lombok.Builder;

import java.util.List;

/**
 * fridge_server의 {@code CoreServerGroceryItemResponse}와 1:1 대응하는 결과 DTO입니다.
 *
 * <h3>전 필드 노출 — 카테고리 규칙까지 포함</h3>
 * <ul>
 *   <li>{@code id} — Grocery item ID</li>
 *   <li>{@code name} — 식재료명</li>
 *   <li>{@code category} — Minor category의 item_type 문자열</li>
 *   <li>{@code defaultUnit} — 기본 단위 코드 (ML/G/EA, nullable)</li>
 *   <li>{@code minPortionAmount}, {@code maxPortionAmount} — 1회 분량 범위 (nullable)</li>
 *   <li>{@code spoonVolumeMl} — 1 TABLESPOON 부피 (nullable)</li>
 *   <li>{@code pieceWeightGram} — 1개당 중량 (nullable, Layer 2/3에서 채움 — TODO)</li>
 *   <li>{@code allowedUsageTypes} — 노출 가능 UsageSpec 문자열 리스트 (nullable)</li>
 *   <li>{@code defaultUsageType} — 첫 진입 시 활성화될 UsageSpec (nullable)</li>
 * </ul>
 *
 * <h3>productId</h3>
 * <p>
 * 이번 범위에서 productId는 항상 null로 송신합니다. 추후 Product BC 연동 시
 * {@code REFProduct} 매핑 결과를 함께 채울 예정입니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 7. (확장: 2026. 5. 9.)
 */
@Builder
public record REFGroceryItemMetadataResult(
        Long id,
        String name,
        String category,
        String defaultUnit,
        Integer minPortionAmount,
        Integer maxPortionAmount,
        Integer spoonVolumeMl,
        Integer pieceWeightGram,
        List<String> allowedUsageTypes,
        String defaultUsageType
) {
}