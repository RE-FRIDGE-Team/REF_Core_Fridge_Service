package com.refridge.core_server.product.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.Optional;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class REFProductName {

    @Column(name = "product_name", nullable = false, length = 200)
    private String value;

    public static REFProductName of(String value) {
        return Optional.ofNullable(value)
                .filter(name -> !name.trim().isEmpty() && name.length() <= 200)
                .map(REFProductName::new)
                .orElseThrow(() -> new IllegalArgumentException(
                        "제품명은 1자 이상 200자 이하여야 합니다."
                ));
    }
}