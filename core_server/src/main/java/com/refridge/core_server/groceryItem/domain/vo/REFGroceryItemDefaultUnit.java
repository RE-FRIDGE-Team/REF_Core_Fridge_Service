package com.refridge.core_server.groceryItem.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 식재료의 기본 사용량 단위.
 *
 * <h3>역할</h3>
 * <p>
 * fridge_server의 요리(Cook) 기능에서 {@code QuantityUsage}, {@code SpoonUsage} 변환 시
 * 어떤 단위(ml / g / 개)로 환산할지 결정하는 메타데이터입니다.
 * </p>
 *
 * <h3>SpoonVolume 해석과의 관계</h3>
 * <p>
 * core_server는 {@code spoonVolumeMl} 필드 하나로 통일해 저장하지만,
 * {@code defaultUnit}이 {@link #G}이면 fridge_server 어댑터가 g으로 해석합니다.
 * 즉 spoonVolumeMl=12, defaultUnit=G → "1스푼 = 12g"으로 의미가 바뀝니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 7.
 */
@Getter
@AllArgsConstructor
public enum REFGroceryItemDefaultUnit {

    /** 부피 (밀리리터) — 액상류: 간장, 식용유, 우유 등 */
    ML("ML"),

    /** 중량 (그램) — 가루/장류/육류/채소 등 */
    G("G"),

    /** 개수 — 달걀, 만두 등 명확하게 셀 수 있는 품목 */
    EA("EA");

    private final String unitCode;

    public static REFGroceryItemDefaultUnit fromUnitCode(String unitCode) {
        return Optional.ofNullable(unitCode)
                .flatMap(code -> Arrays.stream(values())
                        .filter(unit -> unit.unitCode.equals(code))
                        .findAny())
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않은 defaultUnit 코드: " + unitCode));
    }
}