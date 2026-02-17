package com.refridge.core_server.product.domain.vo;

import com.refridge.core_server.product.infra.converter.REFProductUnitConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 제품 용량 Value Object
 * Volume/Weight 단위만 관리 (ml, l, g, kg, mg)
 * 수량 단위(개, 병 등)는 REFProductQuantity에서 별도 관리
 * ⚠️ 파싱 책임 없음:
 * - "500ml" 파싱은 도메인 서비스(Preprocessor)에서 담당
 * - REFProductVolume은 이미 파싱된 값(숫자 + 단위)만 저장
 * 구조:
 * - numericValue: 숫자 (BigDecimal) - 정확한 소수점 계산
 * - unit: 단위 Enum (MILLILITER, KILOGRAM 등)
 * 생성 방법:
 * - of(BigDecimal, REFProductUnit) ← 도메인 서비스가 파싱 후 호출
 * - of(int, REFProductUnit) ← 편의 메서드
 * - of(double, REFProductUnit) ← 편의 메서드
 * - empty() ← 용량 없음
 * 예시:
 * - of(new BigDecimal(500), MILLILITER) → 500ml
 * - of(1.5, KILOGRAM) → 1.5kg
 * - empty() → 용량 없음
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class REFProductVolume {

    /**
     * 숫자 값 (500, 1.5, 200 등)
     * BigDecimal: 정확한 소수점 계산
     */
    @Column(name = "volume_numeric", precision = 10, scale = 2)
    private BigDecimal numericValue;

    /**
     * 단위 (MILLILITER, KILOGRAM 등)
     * ⚠️ NO_UNIT은 허용하지 않음 (Volume은 항상 단위 필요)
     */
    @Column(name = "volume_unit", length = 10)
    @Convert(converter = REFProductUnitConverter.class)
    private REFProductUnit unit;

    /* ===== Factory Methods ===== */

    /**
     * 숫자 + 단위로 생성 (BigDecimal)
     * ⚠️ 도메인 서비스(Preprocessor)가 파싱 후 호출
     *
     * @param numericValue 숫자 값 (필수)
     * @param unit 단위 (필수, NO_UNIT 불가)
     * @return REFProductVolume
     * @throws IllegalArgumentException numericValue나 unit이 null이거나 unit이 NO_UNIT인 경우
     * 예시:
     * - of(new BigDecimal(500), MILLILITER) → "500ml"
     * - of(new BigDecimal("1.5"), KILOGRAM) → "1.5kg"
     */
    public static REFProductVolume of(BigDecimal numericValue, REFProductUnit unit) {
        validateNotNull(numericValue, "용량 값은 필수입니다.");
        validateNotNull(unit, "단위는 필수입니다.");
        return new REFProductVolume(numericValue, unit);
    }

    /**
     * int + 단위로 생성 (편의 메서드)
     *
     * @param numericValue 정수 값
     * @param unit 단위
     * @return REFProductVolume
     *
     * 예시:
     * - of(500, MILLILITER) → "500ml"
     * - of(200, GRAM) → "200g"
     */
    public static REFProductVolume of(int numericValue, REFProductUnit unit) {
        return of(BigDecimal.valueOf(numericValue), unit);
    }

    /**
     * double + 단위로 생성 (편의 메서드)
     *
     * @param numericValue 실수 값
     * @param unit 단위
     * @return REFProductVolume
     *
     * 예시:
     * - of(1.5, KILOGRAM) → "1.5kg"
     * - of(0.5, LITER) → "0.5l"
     */
    public static REFProductVolume of(double numericValue, REFProductUnit unit) {
        return of(BigDecimal.valueOf(numericValue), unit);
    }


    /* ===== Query Methods ===== */

    /**
     * 비어있는지 확인
     * @return numericValue나 unit이 하나라도 null이면 true
     */
    public boolean isEmpty() {
        return numericValue == null || unit == null;
    }

    /**
     * 값이 있는지 확인
     * @return numericValue와 unit 둘 다 있으면 true
     */
    public boolean isPresent() {
        return !isEmpty();
    }

    /**
     * 숫자 값 조회
     * @return Optional<BigDecimal>
     * 예시:
     * - "500ml" → Optional[500]
     * - empty() → Optional.empty()
     */
    public Optional<BigDecimal> getNumericValue() {
        return Optional.ofNullable(numericValue);
    }

    /**
     * 단위 조회
     * @return Optional<REFProductUnit>
     * 예시:
     * - "500ml" → Optional[MILLILITER]
     * - empty() → Optional.empty()
     */
    public Optional<REFProductUnit> getUnit() {
        return Optional.ofNullable(unit);
    }

    /**
     * 문자열 표시 (숫자 + 단위)
     * @return "500ml", "1.5kg" 등
     * 예시:
     * - numericValue: 500, unit: MILLILITER → "500ml"
     * - numericValue: 1.5, unit: KILOGRAM → "1.5kg"
     * - empty() → ""
     */
    public String toDisplayString() {
        if (isEmpty()) {
            return "";
        }
        return numericValue.toPlainString() + unit.getSymbol();
    }

    /**
     * 부피 단위인지 확인 (ml, l)
     *
     * @return unit이 부피 단위면 true
     */
    public boolean isVolumeUnit() {
        return unit != null && unit.isVolume();
    }

    /**
     * 무게 단위인지 확인 (g, kg, mg)
     *
     * @return unit이 무게 단위면 true
     */
    public boolean isWeightUnit() {
        return unit != null && unit.isWeight();
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    /* ===== Private Helpers ===== */

    private static void validateNotNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}