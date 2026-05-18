package com.refridge.core_server.groceryItem.domain.vo;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * RE:FRIDGE의 모든 사용량 단위를 통합 표현하는 enum입니다.
 *
 * <h3>역할 분리 (UnitRole)</h3>
 * <ul>
 *   <li>{@link UnitRole#BASE} — "최종 차감/표시 단위". {@code defaultUnit} 후보가 될 수 있습니다.
 *       (g, kg, ml, L, 개, 알, 마리, 모, 장, 캔)</li>
 *   <li>{@link UnitRole#INPUT} — "입력 편의 단위". {@code allowedUsageUnits}와
 *       {@code defaultUsageUnit}에는 올 수 있지만 {@code defaultUnit}으로는 올 수 없습니다.
 *       (스푼, 티스푼, 컵, 밥그릇, 비율, 전량)</li>
 * </ul>
 *
 * <h3>카테고리 분류 (UnitCategory)</h3>
 * <ul>
 *   <li>{@link UnitCategory#WEIGHT} — 중량 (g, kg, 밥그릇 등)</li>
 *   <li>{@link UnitCategory#VOLUME} — 부피 (ml, L, 스푼, 컵 등)</li>
 *   <li>{@link UnitCategory#COUNT} — 개수 (개, 알, 마리, 모 등)</li>
 *   <li>{@link UnitCategory#ABSTRACT} — 추상 (비율, 전량)</li>
 * </ul>
 *
 * <h3>환산값 (conversionAmount + baseUnit)</h3>
 * <p>
 * 전역적으로 결정 가능한 단위(KG=1000g, CUP=200ml, TABLESPOON=15ml 등)는
 * {@code conversionAmount}와 {@code baseUnit}을 보유합니다.
 * 식재료별로 달라지는 단위(EGG, FISH, TOFU_BLOCK 등)는 두 값이 null이며,
 * 각 GroceryItem의 {@code pieceWeightGram} 또는 향후 추가될 환산 컬럼을 참조합니다.
 * </p>
 *
 * <h3>fridge_server와의 관계</h3>
 * <p>
 * core_server는 응답에서 이 enum의 {@code code} (예: {@code "EGG"}, {@code "TABLESPOON"})
 * 만 String으로 전달합니다. fridge_server는 받은 코드를 자체 단위 체계와 매핑하여
 * {@code UsageSpec sealed interface}로 변환하는 책임을 집니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Getter
public enum REFUsageUnit {

    /* ───────── BASE — 정량 (WEIGHT / VOLUME) ───────── */
    GRAM        ("GRAM",       "g",    UnitCategory.WEIGHT, UnitRole.BASE,  1,    null),
    KILOGRAM    ("KILOGRAM",   "kg",   UnitCategory.WEIGHT, UnitRole.BASE,  1000, "GRAM"),
    MILLILITER  ("MILLILITER", "ml",   UnitCategory.VOLUME, UnitRole.BASE,  1,    null),
    LITER       ("LITER",      "L",    UnitCategory.VOLUME, UnitRole.BASE,  1000, "MILLILITER"),

    /* ───────── BASE — 개수 계열 (식재료별 pieceWeightGram 필요) ───────── */
    PIECE        ("PIECE",        "개",    UnitCategory.COUNT, UnitRole.BASE, null, null),
    EGG          ("EGG",          "알",    UnitCategory.COUNT, UnitRole.BASE, null, null),
    FISH         ("FISH",         "마리",  UnitCategory.COUNT, UnitRole.BASE, null, null),
    TOFU_BLOCK   ("TOFU_BLOCK",   "모",    UnitCategory.COUNT, UnitRole.BASE, null, null),
    CHEESE_SLICE ("CHEESE_SLICE", "장",    UnitCategory.COUNT, UnitRole.BASE, null, null),
    CAN          ("CAN",          "캔",    UnitCategory.COUNT, UnitRole.BASE, null, null),

    /* ───────── INPUT — 부피·중량 전역 환산 ───────── */
    TABLESPOON ("TABLESPOON", "스푼",   UnitCategory.VOLUME, UnitRole.INPUT, 15,  "MILLILITER"),
    TEASPOON   ("TEASPOON",   "티스푼", UnitCategory.VOLUME, UnitRole.INPUT, 5,   "MILLILITER"),
    CUP        ("CUP",        "컵",    UnitCategory.VOLUME, UnitRole.INPUT, 200, "MILLILITER"),
    BOWL       ("BOWL",       "밥그릇", UnitCategory.WEIGHT, UnitRole.INPUT, 210, "GRAM"),

    /* ───────── INPUT — 추상 (변환값 없음) ───────── */
    RATIO ("RATIO", "비율", UnitCategory.ABSTRACT, UnitRole.INPUT, null, null),
    FULL  ("FULL",  "전량", UnitCategory.ABSTRACT, UnitRole.INPUT, null, null);

    private final String code;
    private final String displayName;
    private final UnitCategory category;
    private final UnitRole role;

    /** 이 단위 1개 = N개의 baseUnit (글로벌 환산값). null이면 식재료별 환산값 필요. */
    private final Integer conversionAmount;

    /**
     * 환산 대상 BASE 단위의 code. forward reference 회피를 위해 String으로 저장하며
     * {@link #getBaseUnit()} 호출 시 lazy 해석됩니다.
     */
    private final String baseUnitCode;

    REFUsageUnit(String code, String displayName,
                 UnitCategory category, UnitRole role,
                 Integer conversionAmount, String baseUnitCode) {
        this.code = code;
        this.displayName = displayName;
        this.category = category;
        this.role = role;
        this.conversionAmount = conversionAmount;
        this.baseUnitCode = baseUnitCode;
    }

    /* ──────────────────── PUBLIC API ──────────────────── */

    public boolean isBaseUnit() {
        return role == UnitRole.BASE;
    }

    public boolean isInputUnit() {
        return role == UnitRole.INPUT;
    }

    /** 글로벌 환산값을 보유한 단위인지 (KG, L, 스푼류, 컵, 밥그릇). */
    public boolean hasGlobalConversion() {
        return conversionAmount != null && baseUnitCode != null;
    }

    /** 식재료별 환산값이 필요한 단위인지 (EGG, FISH, TOFU_BLOCK 등). */
    public boolean requiresPerItemConversion() {
        return category == UnitCategory.COUNT && conversionAmount == null;
    }

    /** {@code baseUnit} 코드로부터 enum 인스턴스 해석. */
    public Optional<REFUsageUnit> getBaseUnit() {
        return Optional.ofNullable(baseUnitCode).flatMap(REFUsageUnit::fromCodeOptional);
    }

    /* ──────────────────── STATIC LOOKUP ──────────────────── */

    public static REFUsageUnit fromCode(String code) {
        return fromCodeOptional(code).orElseThrow(() ->
                new IllegalArgumentException("유효하지 않은 REFUsageUnit 코드: " + code));
    }

    public static Optional<REFUsageUnit> fromCodeOptional(String code) {
        return Optional.ofNullable(code)
                .flatMap(c -> Arrays.stream(values())
                        .filter(u -> u.code.equals(c))
                        .findAny());
    }

    /* ──────────────────── ENUM TYPES ──────────────────── */

    public enum UnitCategory {
        WEIGHT, VOLUME, COUNT, ABSTRACT
    }

    public enum UnitRole {
        /** defaultUnit으로 사용 가능한 최종 차감/표시 단위 */
        BASE,
        /** allowedUsageUnits에는 올 수 있으나 defaultUnit으로는 못 옴 */
        INPUT
    }
}