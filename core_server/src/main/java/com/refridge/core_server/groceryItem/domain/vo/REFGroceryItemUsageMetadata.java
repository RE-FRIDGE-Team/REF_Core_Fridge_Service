package com.refridge.core_server.groceryItem.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 식재료 사용량 환산 메타데이터를 묶은 Value Object입니다.
 *
 * <h3>구성 필드</h3>
 * <ul>
 *   <li>{@code defaultUnit} — 최종 차감/표시 단위 ({@link REFUsageUnit.UnitRole#BASE}만 허용)</li>
 *   <li>{@code minPortionAmount}, {@code maxPortionAmount} — 1회 합리적 사용량 범위
 *       (fridge_server가 후보군 생성 시 가이드)</li>
 *   <li>{@code pieceWeightGram} — 1개당 g (defaultUnit이 COUNT 계열일 때 g 환산용)</li>
 *   <li>{@code allowedUsageUnitsCsv} — 입력 가능한 단위 코드 CSV (BASE/INPUT 모두 가능)</li>
 *   <li>{@code defaultUsageUnit} — 첫 진입 시 활성화될 단위 (BASE 또는 INPUT 가능)</li>
 * </ul>
 *
 * <h3>{@code defaultUnit} 제약</h3>
 * <p>
 * {@code defaultUnit}은 반드시 {@link REFUsageUnit#isBaseUnit()}이 true인 단위여야 합니다.
 * 스푼/티스푼/컵/밥그릇/비율/전량 같은 INPUT 단위는 입력 편의용이지 최종 차감 단위가 될 수 없습니다.
 * </p>
 *
 * <h3>저장 전략 — allowedUsageUnits</h3>
 * <p>
 * 별도 테이블을 만들지 않고 CSV 문자열로 저장하여 VO 응집도와 단순함을 유지합니다.
 * 예: {@code "TABLESPOON,TEASPOON,CUP,MILLILITER"}.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryItemUsageMetadata {

    private static final String CSV_DELIMITER = ",";

    @Enumerated(EnumType.STRING)
    @Column(name = "default_unit", length = 16)
    private REFUsageUnit defaultUnit;

    @Column(name = "min_portion_amount")
    private Integer minPortionAmount;

    @Column(name = "max_portion_amount")
    private Integer maxPortionAmount;

    @Column(name = "piece_weight_gram")
    private Integer pieceWeightGram;

    @Column(name = "allowed_usage_units_csv", length = 128)
    private String allowedUsageUnitsCsv;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_usage_unit", length = 16)
    private REFUsageUnit defaultUsageUnit;

    /* ──────────────────── FACTORY METHODS ──────────────────── */

    /** 모든 필드가 null인 빈 메타데이터. */
    public static REFGroceryItemUsageMetadata empty() {
        return new REFGroceryItemUsageMetadata(null, null, null, null, null, null);
    }

    /** 명시적 값으로 메타데이터를 구성합니다. */
    public static REFGroceryItemUsageMetadata of(
            REFUsageUnit defaultUnit,
            Integer minPortionAmount,
            Integer maxPortionAmount,
            Integer pieceWeightGram,
            List<REFUsageUnit> allowedUsageUnits,
            REFUsageUnit defaultUsageUnit) {

        validateDefaultUnitIsBase(defaultUnit);
        validatePortionRange(minPortionAmount, maxPortionAmount);
        validatePositive("pieceWeightGram", pieceWeightGram);
        validateAllowedAndDefault(allowedUsageUnits, defaultUsageUnit);

        return new REFGroceryItemUsageMetadata(
                defaultUnit,
                minPortionAmount,
                maxPortionAmount,
                pieceWeightGram,
                toCsv(allowedUsageUnits),
                defaultUsageUnit
        );
    }

    /* ──────────────────── BUSINESS LOGIC (불변 with-style) ──────────────────── */

    public REFGroceryItemUsageMetadata withDefaultUnit(REFUsageUnit newUnit) {
        validateDefaultUnitIsBase(newUnit);
        return new REFGroceryItemUsageMetadata(
                newUnit, this.minPortionAmount, this.maxPortionAmount,
                this.pieceWeightGram, this.allowedUsageUnitsCsv, this.defaultUsageUnit);
    }

    public REFGroceryItemUsageMetadata withPieceWeightGram(Integer newPieceWeight) {
        validatePositive("pieceWeightGram", newPieceWeight);
        return new REFGroceryItemUsageMetadata(
                this.defaultUnit, this.minPortionAmount, this.maxPortionAmount,
                newPieceWeight, this.allowedUsageUnitsCsv, this.defaultUsageUnit);
    }

    public REFGroceryItemUsageMetadata withPortionRange(Integer min, Integer max) {
        validatePortionRange(min, max);
        return new REFGroceryItemUsageMetadata(
                this.defaultUnit, min, max,
                this.pieceWeightGram, this.allowedUsageUnitsCsv, this.defaultUsageUnit);
    }

    public REFGroceryItemUsageMetadata withAllowedUsageUnits(
            List<REFUsageUnit> newAllowed, REFUsageUnit newDefault) {
        validateAllowedAndDefault(newAllowed, newDefault);
        return new REFGroceryItemUsageMetadata(
                this.defaultUnit, this.minPortionAmount, this.maxPortionAmount,
                this.pieceWeightGram, toCsv(newAllowed), newDefault);
    }

    /**
     * 기존 allowed list에 새 단위들을 추가한 메타데이터를 반환합니다.
     * 특수 케이스 resolver가 "두부에 모 추가" 같은 식으로 사용합니다.
     * 중복은 제거되며 순서는 기존 → 신규 순으로 유지됩니다.
     */
    public REFGroceryItemUsageMetadata appendAllowedUsageUnits(REFUsageUnit... toAppend) {
        List<REFUsageUnit> current = getAllowedUsageUnits();
        java.util.LinkedHashSet<REFUsageUnit> merged = new java.util.LinkedHashSet<>(current);
        for (REFUsageUnit u : toAppend) {
            if (u != null) merged.add(u);
        }
        return new REFGroceryItemUsageMetadata(
                this.defaultUnit, this.minPortionAmount, this.maxPortionAmount,
                this.pieceWeightGram, toCsv(List.copyOf(merged)), this.defaultUsageUnit);
    }

    /* ──────────────────── QUERY ──────────────────── */

    public boolean hasPieceWeight() {
        return pieceWeightGram != null;
    }

    public boolean hasUsageUnits() {
        return allowedUsageUnitsCsv != null && !allowedUsageUnitsCsv.isBlank();
    }

    public List<REFUsageUnit> getAllowedUsageUnits() {
        if (!hasUsageUnits()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedUsageUnitsCsv.split(CSV_DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(REFUsageUnit::fromCode)
                .toList();
    }

    public Optional<REFUsageUnit> defaultUnitOptional() {
        return Optional.ofNullable(defaultUnit);
    }

    public Optional<REFUsageUnit> defaultUsageUnitOptional() {
        return Optional.ofNullable(defaultUsageUnit);
    }

    /* ──────────────────── VALIDATION ──────────────────── */

    private static void validateDefaultUnitIsBase(REFUsageUnit unit) {
        if (unit != null && !unit.isBaseUnit()) {
            throw new IllegalArgumentException(
                    "defaultUnit은 BASE 단위여야 합니다 (g/kg/ml/L/개/알/마리/모/장/캔). " +
                            "입력값: " + unit + " (" + unit.getRole() + ")");
        }
    }

    private static void validatePortionRange(Integer min, Integer max) {
        if (min != null && min < 0) {
            throw new IllegalArgumentException("minPortionAmount는 음수일 수 없습니다: " + min);
        }
        if (max != null && max < 0) {
            throw new IllegalArgumentException("maxPortionAmount는 음수일 수 없습니다: " + max);
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException(String.format(
                    "minPortionAmount(%d) > maxPortionAmount(%d)", min, max));
        }
    }

    private static void validatePositive(String fieldName, Integer value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(fieldName + "은 양수여야 합니다: " + value);
        }
    }

    private static void validateAllowedAndDefault(
            List<REFUsageUnit> allowed, REFUsageUnit defaultUnit) {

        if (allowed == null || allowed.isEmpty()) {
            if (defaultUnit != null) {
                throw new IllegalArgumentException(
                        "defaultUsageUnit이 지정되었으나 allowedUsageUnits가 비어 있습니다.");
            }
            return;
        }
        if (defaultUnit == null) {
            throw new IllegalArgumentException(
                    "allowedUsageUnits가 있으면 defaultUsageUnit도 지정되어야 합니다.");
        }
        if (!allowed.contains(defaultUnit)) {
            throw new IllegalArgumentException(
                    "defaultUsageUnit은 allowedUsageUnits에 포함되어야 합니다: " + defaultUnit);
        }
    }

    private static String toCsv(List<REFUsageUnit> units) {
        if (units == null || units.isEmpty()) {
            return null;
        }
        return units.stream()
                .map(REFUsageUnit::getCode)
                .reduce((a, b) -> a + CSV_DELIMITER + b)
                .orElse(null);
    }
}