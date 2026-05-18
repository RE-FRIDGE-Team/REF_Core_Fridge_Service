package com.refridge.core_server.groceryItem.presentation;

import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemUsageMetadataUpdateCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 사용량 메타데이터 PUT 요청 본문.
 * <p>
 * 모든 필드는 nullable이며 검증은 포맷/범위 정도만 수행합니다.
 * 도메인 무결성 검증은 {@code REFGroceryItemUsageMetadata.of()}에서 수행됩니다.
 * </p>
 */
public record REFGroceryItemMetadataUpdateRequest(

        @Pattern(regexp = "ML|G|EA",
                message = "defaultUnit은 ML / G / EA 중 하나여야 합니다.")
        String defaultUnit,

        @Min(value = 0, message = "minPortionAmount는 0 이상이어야 합니다.")
        Integer minPortionAmount,

        @Min(value = 0, message = "maxPortionAmount는 0 이상이어야 합니다.")
        Integer maxPortionAmount,

        @Min(value = 1, message = "spoonVolumeMl은 1 이상이어야 합니다.")
        Integer spoonVolumeMl,

        @Min(value = 1, message = "pieceWeightGram은 1 이상이어야 합니다.")
        Integer pieceWeightGram,

        List<String> allowedUsageTypes,

        @Pattern(regexp = "FULL|COUNT|QUANTITY|RATIO|SPOON",
                message = "defaultUsageType은 FULL/COUNT/QUANTITY/RATIO/SPOON 중 하나여야 합니다.")
        String defaultUsageType
) {

    public REFGroceryItemUsageMetadataUpdateCommand toCommand(Long groceryItemId) {
        return REFGroceryItemUsageMetadataUpdateCommand.builder()
                .groceryItemId(groceryItemId)
                .defaultUnit(this.defaultUnit)
                .minPortionAmount(this.minPortionAmount)
                .maxPortionAmount(this.maxPortionAmount)
                .spoonVolumeMl(this.spoonVolumeMl)
                .pieceWeightGram(this.pieceWeightGram)
                .allowedUsageTypes(this.allowedUsageTypes)
                .defaultUsageType(this.defaultUsageType)
                .build();
    }
}