package com.refridge.core_server.groceryItem.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemMetadataResult;
import lombok.Builder;

import java.util.List;

/**
 * fridge_server의 {@code CoreServerGroceryItemResponse}와 1:1 대응하는 REST 응답 DTO입니다.
 *
 * <h3>응답 예시 (SAUCE_SEASONING — 간장)</h3>
 * <pre>{@code
 * {
 *   "id": 1,
 *   "name": "간장",
 *   "category": "SAUCE_SEASONING",
 *   "defaultUnit": "ML",
 *   "minPortionAmount": null,
 *   "maxPortionAmount": null,
 *   "spoonVolumeMl": 15,
 *   "pieceWeightGram": null,
 *   "allowedUsageTypes": ["SPOON","QUANTITY"],
 *   "defaultUsageType": "SPOON",
 *   "productId": null
 * }
 * }</pre>
 *
 * <h3>응답 예시 (DAIRY — 계란)</h3>
 * <pre>{@code
 * {
 *   "id": 42,
 *   "name": "계란",
 *   "category": "DAIRY",
 *   "defaultUnit": "ML",
 *   "spoonVolumeMl": null,
 *   "pieceWeightGram": null,
 *   "allowedUsageTypes": ["COUNT","QUANTITY","RATIO"],
 *   "defaultUsageType": "COUNT",
 *   "productId": null
 * }
 * }</pre>
 *
 * <h3>productId</h3>
 * <p>
 * 이번 범위에서 항상 null입니다. Product BC 연동 시 채워질 예정입니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 7. (확장: 2026. 5. 9.)
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
        List<String> allowedUsageTypes,
        Integer pieceWeightGram,
        Integer spoonVolumeMl,
        String defaultUsageType,
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
                .allowedUsageTypes(result.allowedUsageTypes())
                .pieceWeightGram(result.pieceWeightGram())
                .spoonVolumeMl(result.spoonVolumeMl())
                .defaultUsageType(result.defaultUsageType())
                // Product BC 연동 후 채워질 예정 (TODO)
                .productId(null)
                .build();
    }
}