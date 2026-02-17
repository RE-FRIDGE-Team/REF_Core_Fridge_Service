package com.refridge.core_server.product.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 제품 수량 단위
 * 개별 제품 포장 단위를 표현
 * 예시:
 * - "에비앙 24개" → PIECE (개)
 * - "소주 2병" → BOTTLE (병)
 * - "라면 5팩" → PACK (팩)
 * - "콜라 12캔" → CAN (캔)
 */
@Getter
@AllArgsConstructor
public enum REFProductQuantityUnit {

    /* ===== 일반 단위 ===== */
    PIECE("개", "개", UnitCategory.GENERAL),
    BOTTLE("병", "병", UnitCategory.BEVERAGE),
    PACK("팩", "팩", UnitCategory.PACKAGE),
    CAN("캔", "캔", UnitCategory.BEVERAGE),
    BAG("봉지", "봉지", UnitCategory.PACKAGE),
    BOX("박스", "박스", UnitCategory.PACKAGE),
    GAEIP("개입", "개입", UnitCategory.PACKAGE),  // 1박스에 몇 개 들어있는지 표현할 때 사용

    /* ===== 식품 특화 ===== */
    ANIMAL("마리", "마리", UnitCategory.FOOD),    // 생선, 닭
    BUNCH("묶음", "묶음", UnitCategory.FOOD),      // 채소
    HEAD("포기", "포기", UnitCategory.FOOD),       // 배추, 양배추

    /* ===== 기타 ===== */
    SET("세트", "세트", UnitCategory.OTHER),
    ROLL("롤", "롤", UnitCategory.OTHER);

    private final String symbol;           // "개", "병"
    private final String displayName;      // "개", "병"
    private final UnitCategory category;   // 카테고리

    /**
     * 심볼로 Unit 찾기
     * "개" → PIECE
     * "병" → BOTTLE
     */
    public static Optional<REFProductQuantityUnit> fromSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalized = symbol.trim();

        return Arrays.stream(REFProductQuantityUnit.values())
                .filter(unit -> unit.getSymbol().equals(normalized))
                .findFirst();
    }

    /**
     * 일반 단위인지 확인 (개, 박스 등)
     */
    public boolean isGeneral() {
        return this.category == UnitCategory.GENERAL ||
                this.category == UnitCategory.PACKAGE;
    }

    /**
     * 음료 단위인지 확인 (병, 캔)
     */
    public boolean isBeverage() {
        return this.category == UnitCategory.BEVERAGE;
    }

    /**
     * 식품 단위인지 확인 (마리, 포기)
     */
    public boolean isFood() {
        return this.category == UnitCategory.FOOD;
    }

    /**
     * 단위 카테고리
     */
    public enum UnitCategory {
        GENERAL("일반"),
        PACKAGE("포장"),
        BEVERAGE("음료"),
        FOOD("식품"),
        OTHER("기타");

        @Getter
        private final String description;

        UnitCategory(String description) {
            this.description = description;
        }
    }

    @Override
    public String toString() {
        return symbol;
    }
}
