package com.refridge.core_server.product.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.Optional;

/**
 * 브랜드명 Value Object
 * - 제조사/판매사 브랜드
 * - null 가능 (비가공품은 브랜드 없음)
 * - 예: "델몬트", "농심", "곰곰"
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class REFBrandName {

    @Column(name = "brand_name", length = 100)
    private String value;

    public static REFBrandName of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return empty();
        }

        return Optional.of(value)
                .filter(name -> name.length() <= 100)
                .map(REFBrandName::new)
                .orElseThrow(() -> new IllegalArgumentException(
                        "브랜드명은 100자 이하여야 합니다."
                ));
    }

    public static REFBrandName empty() {
        return new REFBrandName(null);
    }

    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

    public boolean isPresent() {
        return !isEmpty();
    }
}