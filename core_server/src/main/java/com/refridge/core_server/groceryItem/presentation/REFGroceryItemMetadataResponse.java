package com.refridge.core_server.groceryItem.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemMetadataResult;
import lombok.Builder;

import java.util.List;

/**
 * fridge_server의 {@code CoreServerGroceryItemResponse}와 1:1 대응하는 REST 응답 DTO입니다.
 *
 * <h3>응답 예시 (계란)</h3>
 * <pre>{@code
 * {
 *   "id": 50, "name": "계란", "category": "DAIRY",
 *   "defaultUnit": "EGG",
 *   "minPortionAmount": 1, "maxPortionAmount": 12,
 *   "allowedUsageUnits": ["EGG","GRAM"],
 *   "defaultUsageUnit": "EGG",
 *   "pieceWeightGram": 60,
 *   "productId": null
 * }
 * }</pre>
 *
 * <h3>응답 예시 (간장)</h3>
 * <pre>{@code
 * {
 *   "id": 1, "name": "간장", "category": "SAUCE_SEASONING",
 *   "defaultUnit": "ML",
 *   "allowedUsageUnits": ["TABLESPOON","TEASPOON","CUP","MILLILITER"],
 *   "defaultUsageUnit": "TABLESPOON",
 *   "productId": null
 * }
 * }</pre>
 *
 * <h3>응답 예시 (쌀)</h3>
 * <pre>{@code
 * {
 *   "id": 100, "name": "쌀", "category": "GRAIN",
 *   "defaultUnit": "G",
 *   "allowedUsageUnits": ["CUP","GRAM","KILOGRAM"],
 *   "defaultUsageUnit": "CUP",
 *   "productId": null
 * }
 * }</pre>
 *
 * <h3>productId</h3>
 * <p>
 * 이번 범위에서 항상 null. Product BC 연동 시 채워질 예정 (TODO).
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record REFGroceryItemMetadataResponse(
        Long id,
        String name,
        String category,
        String defaultUnit,
        Integer minPortionAmount,
        Integer maxPortionAmount,
        Integer pieceWeightGram,
        List<String> allowedUsageUnits,
        String defaultUsageUnit,
        Long productId
) {

    public static REFGroceryItemMetadataResponse from(REFGroceryItemMetadataResult result) {
        return REFGroceryItemMetadataResponse.builder()
                .id(result.id())
                .name(result.name())
                .category(result.category())
                .defaultUnit(result.defaultUnit())
                .minPortionAmount(result.minPortionAmount())
                .maxPortionAmount(result.maxPortionAmount())
                .pieceWeightGram(result.pieceWeightGram())
                .allowedUsageUnits(result.allowedUsageUnits())
                .defaultUsageUnit(result.defaultUsageUnit())
                .productId(null)   // Product BC 연동 후 채워질 예정 (TODO)
                .build();
    }
}