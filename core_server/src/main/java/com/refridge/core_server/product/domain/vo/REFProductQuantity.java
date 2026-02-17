package com.refridge.core_server.product.domain.vo;

import com.refridge.core_server.product.infra.converter.REFProductQuantityUnitConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.Optional;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class REFProductQuantity {

    /**
     * 기본 수량 (필수)
     */
    @Column(name = "quantity_numeric", nullable = false)
    private Integer numericValue;

    /**
     * 수량 단위 (필수)
     */
    @Column(name = "quantity_unit", nullable = false, length = 10)
    @Convert(converter = REFProductQuantityUnitConverter.class)
    private REFProductQuantityUnit unit;

    /**
     * 프로모션 보너스 수량 (선택)
     * "2+1" → bonusCount: 1
     */
    @Column(name = "quantity_bonus")
    private Integer bonusCount;

    /* ===== Factory Methods ===== */

    /**
     * 숫자 + 단위로 생성
     * ⚠️ 도메인 서비스(Preprocessor)가 파싱 후 호출
     * @param numericValue 개수 (필수)
     * @param unit 단위 (필수)
     * @return REFProductQuantity
     *
     * 예시:
     * - of(24, PIECE) → "24개"
     * - of(2, BOTTLE) → "2병"
     */
    public static REFProductQuantity of(Integer numericValue, REFProductQuantityUnit unit) {
        validateNotNull(numericValue, "수량 값은 필수입니다.");
        validateNotNull(unit, "단위는 필수입니다.");
        validatePositive(numericValue, "수량은 양수여야 합니다.");

        return new REFProductQuantity(numericValue, unit, null);
    }

    /**
     * 숫자 + 단위 + 보너스로 생성 (프로모션)
     *
     * @param numericValue 기본 개수
     * @param bonusCount 보너스 개수
     * @param unit 단위
     * @return REFProductQuantity
     *
     * 예시:
     * - of(2, 1, PIECE) → "2+1개"
     */
    public static REFProductQuantity ofPromotion(
            Integer numericValue,
            Integer bonusCount,
            REFProductQuantityUnit unit
    ) {
        validateNotNull(numericValue, "수량 값은 필수입니다.");
        validateNotNull(bonusCount, "보너스 수량은 필수입니다.");
        validateNotNull(unit, "단위는 필수입니다.");
        validatePositive(numericValue, "수량은 양수여야 합니다.");
        validatePositive(bonusCount, "보너스 수량은 양수여야 합니다.");

        return new REFProductQuantity(numericValue, unit, bonusCount);
    }

    /**
     * 기본 수량 (1개)
     */
    public static REFProductQuantity defaultQuantity() {
        return new REFProductQuantity(1, REFProductQuantityUnit.PIECE, null);
    }

    /* ===== Query Methods ===== */

    /**
     * 비어있는지 확인
     */
    public boolean isEmpty() {
        return numericValue == null || unit == null;
    }

    /**
     * 값이 있는지 확인
     */
    public boolean isPresent() {
        return !isEmpty();
    }

    /**
     * 기본 수량 조회
     */
    public Optional<Integer> getNumericValue() {
        return Optional.ofNullable(numericValue);
    }

    /**
     * 단위 조회
     */
    public Optional<REFProductQuantityUnit> getUnit() {
        return Optional.ofNullable(unit);
    }

    /**
     * 보너스 수량 조회
     */
    public Optional<Integer> getBonusCount() {
        return Optional.ofNullable(bonusCount);
    }

    /**
     * 프로모션 여부
     */
    public boolean isPromotion() {
        return bonusCount != null && bonusCount > 0;
    }

    /**
     * 전체 수량 (기본 + 보너스)
     * "2+1" → 3
     */
    public int getTotalCount() {
        int base = numericValue != null ? numericValue : 0;
        int bonus = bonusCount != null ? bonusCount : 0;
        return base + bonus;
    }

    /**
     * 문자열 표시
     * @return "24개", "2병", "2+1개"
     * 예시:
     * - numericValue: 24, unit: PIECE → "24개"
     * - numericValue: 2, unit: BOTTLE → "2병"
     * - numericValue: 2, bonusCount: 1, unit: PIECE → "2+1개"
     */
    public String toDisplayString() {
        if (isEmpty()) {
            return "";
        }

        if (isPromotion()) {
            return numericValue + "+" + bonusCount + unit.getSymbol();
        }

        return numericValue + unit.getSymbol();
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

    private static void validatePositive(Integer value, String message) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}