package com.refridge.core_server.groceryItem.application.dto.command;

import lombok.Builder;

import java.util.List;

/**
 * GroceryItem 사용량 메타데이터 일괄 업데이트 커맨드.
 * <p>
 * 모든 필드는 nullable. null이면 "해당 항목 비움(미설정)".
 * </p>
 *
 * <h3>주의</h3>
 * <ul>
 *   <li>{@code defaultUnit}은 반드시 {@code REFUsageUnit} BASE 단위(g/kg/ml/L/개/알/마리/모/장/캔)여야 합니다.
 *       INPUT 단위(스푼, 컵 등)를 보내면 도메인 검증에서 거부됩니다.</li>
 *   <li>{@code allowedUsageUnits}와 {@code defaultUsageUnit}은 함께 지정하거나 함께 null이어야 합니다.</li>
 *   <li>{@code defaultUsageUnit}은 반드시 {@code allowedUsageUnits}에 포함되어야 합니다.</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Builder
public record REFGroceryItemUsageMetadataUpdateCommand(
        Long groceryItemId,
        String defaultUnit,                 // REFUsageUnit code (BASE only)
        Integer minPortionAmount,
        Integer maxPortionAmount,
        Integer pieceWeightGram,
        List<String> allowedUsageUnits,     // REFUsageUnit code 리스트
        String defaultUsageUnit             // REFUsageUnit code (BASE 또는 INPUT)
) {
}