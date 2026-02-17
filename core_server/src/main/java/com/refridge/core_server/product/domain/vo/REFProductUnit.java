package com.refridge.core_server.product.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 제품 용량 단위 (Volume/Weight만)
 * ⚠️ 수량 단위(개, 병 등)는 포함하지 않음
 * → 수량은 REFProductQuantity에서 별도 관리
 * 예시:
 * - "에비앙 330ml, 24개" → volume: "330ml", quantity: "24개"
 * - "엄마밥줘 10kg, 1개" → volume: "10kg", quantity: "1개"
 * 카테고리:
 * - 부피: ml, l
 * - 무게: g, kg, mg
 * - 없음: NO_UNIT (단위 없는 경우)
 */
@Getter
@AllArgsConstructor
public enum REFProductUnit {

    /* ===== 부피 (Volume) ===== */
    MILLILITER("ml", "밀리리터", UnitType.VOLUME),
    LITER("l", "리터", UnitType.VOLUME),

    /* ===== 무게 (Weight) ===== */
    GRAM("g", "그램", UnitType.WEIGHT),
    KILOGRAM("kg", "킬로그램", UnitType.WEIGHT),
    MILLIGRAM("mg", "밀리그램", UnitType.WEIGHT);

    private final String symbol;      // "ml", "kg"
    private final String displayName; // "밀리리터", "킬로그램"
    private final UnitType unitType;  // VOLUME, WEIGHT, NONE

    /**
     * 심볼로 Unit 찾기 (대소문자 무시)
     * "ml" → MILLILITER
     * "ML" → MILLILITER
     * "kg" → KILOGRAM
     */
    public static Optional<REFProductUnit> fromSymbol(String symbol) {
        return Arrays.stream(REFProductUnit.values())
                .filter(unit -> unit.getSymbol().toLowerCase().equals(Optional.ofNullable(symbol)
                        .filter(s -> !s.trim().isEmpty())
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .orElseThrow(() -> new IllegalArgumentException("단위 심볼은 null 또는 빈 문자열이 될 수 없습니다.")
                        )))
                .findFirst();
    }

    /**
     * 부피 단위인지 확인
     */
    public boolean isVolume() {
        return this.unitType == UnitType.VOLUME;
    }

    /**
     * 무게 단위인지 확인
     */
    public boolean isWeight() {
        return this.unitType == UnitType.WEIGHT;
    }

    /**
     * 단위 타입 Enum
     */
    public enum UnitType {
        VOLUME("부피"),
        WEIGHT("무게");

        @Getter
        private final String description;

        UnitType(String description) {
            this.description = description;
        }
    }

    @Override
    public String toString() {
        return symbol;
    }
    }