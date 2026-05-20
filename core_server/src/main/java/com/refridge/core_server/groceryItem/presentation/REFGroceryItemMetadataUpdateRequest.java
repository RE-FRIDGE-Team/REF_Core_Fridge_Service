package com.refridge.core_server.groceryItem.presentation;

import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemUsageMetadataUpdateCommand;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * 사용량 메타데이터 PUT 요청 본문.
 * <p>
 * 단위 코드의 유효성 검증은 도메인 {@code REFUsageUnit.fromCode()}와
 * {@code REFGroceryItemUsageMetadata.of()}가 담당하므로, Bean Validation은
 * 숫자 범위 정도만 가볍게 수행합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
public record REFGroceryItemMetadataUpdateRequest(

        String defaultUnit,                 // REFUsageUnit code, BASE only (도메인 검증)

        @Min(value = 0, message = "minPortionAmount는 0 이상이어야 합니다.")
        Integer minPortionAmount,

        @Min(value = 0, message = "maxPortionAmount는 0 이상이어야 합니다.")
        Integer maxPortionAmount,

        @Min(value = 1, message = "pieceWeightGram은 1 이상이어야 합니다.")
        Integer pieceWeightGram,

        List<String> allowedUsageUnits,     // REFUsageUnit code 리스트

        String defaultUsageUnit             // REFUsageUnit code
) {

    public REFGroceryItemUsageMetadataUpdateCommand toCommand(Long groceryItemId) {
        return REFGroceryItemUsageMetadataUpdateCommand.builder()
                .groceryItemId(groceryItemId)
                .defaultUnit(this.defaultUnit)
                .minPortionAmount(this.minPortionAmount)
                .maxPortionAmount(this.maxPortionAmount)
                .pieceWeightGram(this.pieceWeightGram)
                .allowedUsageUnits(this.allowedUsageUnits)
                .defaultUsageUnit(this.defaultUsageUnit)
                .build();
    }
}