package com.refridge.core_server.groceryItem.application.dto.result;

import lombok.Builder;

import java.util.List;

/**
 * fridge_server의 {@code CoreServerGroceryItemResponse}와 1:1 대응하는 결과 DTO입니다.
 *
 * <h3>필드 의미</h3>
 * <ul>
 *   <li>{@code defaultUnit} — 최종 차감 단위 ({@code REFUsageUnit} BASE only)</li>
 *   <li>{@code minPortionAmount}, {@code maxPortionAmount} — fridge_server가 후보군 생성 시 가이드로 사용하는 1회 합리적 사용 범위</li>
 *   <li>{@code pieceWeightGram} — 1개당 g (defaultUnit이 COUNT 계열일 때 g 환산용)</li>
 *   <li>{@code allowedUsageUnits} — 입력 가능한 단위 코드 리스트 (BASE/INPUT 모두 가능)</li>
 *   <li>{@code defaultUsageUnit} — 첫 진입 시 활성화될 단위 (BASE 또는 INPUT)</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Builder
public record REFGroceryItemMetadataResult(
        Long id,
        String name,
        String category,
        String defaultUnit,
        Integer minPortionAmount,
        Integer maxPortionAmount,
        Integer pieceWeightGram,
        List<String> allowedUsageUnits,
        String defaultUsageUnit
) {
}