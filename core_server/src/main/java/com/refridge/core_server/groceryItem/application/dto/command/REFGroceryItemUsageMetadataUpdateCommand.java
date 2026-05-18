package com.refridge.core_server.groceryItem.application.dto.command;

import lombok.Builder;

import java.util.List;

/**
 * GroceryItem 사용량 환산 메타데이터를 업데이트하는 커맨드입니다.
 * <p>
 * 모든 필드는 nullable이며, null인 필드는 "해당 항목을 비움(미설정)"으로 해석됩니다.
 * </p>
 *
 * <h3>주의</h3>
 * <ul>
 *   <li>{@code allowedUsageTypes}와 {@code defaultUsageType}은 함께 지정하거나 함께 null이어야 합니다.</li>
 *   <li>{@code defaultUsageType}은 반드시 {@code allowedUsageTypes}에 포함되어야 합니다.</li>
 *   <li>SAUCE_SEASONING 계열이 아닌 식재료에 spoonVolumeMl을 설정하는 것은 의미가 없습니다.</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 7. (확장: 2026. 5. 9.)
 */
@Builder
public record REFGroceryItemUsageMetadataUpdateCommand(
        Long groceryItemId,
        String defaultUnit,                 // ML / G / EA, nullable
        Integer minPortionAmount,
        Integer maxPortionAmount,
        Integer spoonVolumeMl,
        Integer pieceWeightGram,
        List<String> allowedUsageTypes,     // ["SPOON","QUANTITY"] 같은 코드 리스트
        String defaultUsageType             // "SPOON" 같은 단일 코드
) {
}