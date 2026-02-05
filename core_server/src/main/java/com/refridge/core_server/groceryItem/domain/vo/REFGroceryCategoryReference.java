package com.refridge.core_server.groceryItem.domain.vo;


import groovy.transform.EqualsAndHashCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryCategoryReference {

    @Column(name = "major_category_id")
    private Long majorCategoryId;

    @Column(name = "minor_category_id")
    private Long minorCategoryId;

    public static REFGroceryCategoryReference of(Long majorCategoryId, Long minorCategoryId) {
        return Optional.ofNullable(majorCategoryId)
                .flatMap(majorId -> Optional.ofNullable(minorCategoryId)
                        .map(minorId -> new REFGroceryCategoryReference(majorId, minorId)))
                .orElseThrow(() -> new IllegalArgumentException("대분류 및 중분류 카테고리 ID는 필수입니다."));
    }

    public Optional<Long> getMajorCategoryIdOptional() {
        return Optional.ofNullable(majorCategoryId);
    }

    public Optional<Long> getMinorCategoryIdOptional() {
        return Optional.ofNullable(minorCategoryId);
    }

}
