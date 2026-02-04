package com.refridge.core_server.grocery_category.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.Optional;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryCategoryName {

    @Column(name = "category_name")
    private String value;

    /* CREATION FACTORY METHOD */
    public static REFGroceryCategoryName of(String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(REFGroceryCategoryName::isValidCategoryNameCondition)
                .map(REFGroceryCategoryName::new)
                .orElseThrow(() -> new IllegalArgumentException(
                        "카테고리 이름은 1자 이상 20자 이하여야 합니다."
                ));
    }

    /* INTERNAL METHOD : 카테고리명 조건 검증 */
    private static boolean isValidCategoryNameCondition(String categoryName) {
        return !categoryName.isEmpty() && categoryName.length() <= 20;
    }
}
