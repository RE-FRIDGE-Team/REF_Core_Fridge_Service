package com.refridge.core_server.product.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class REFGroceryItemReference {

    @Column(name = "grocery_item_id", nullable = false)
    private Long groceryItemId;

    @Column(name = "major_category_id", nullable = false)
    private Long majorCategoryId;

    @Column(name = "minor_category_id", nullable = false)
    private Long minorCategoryId;

    public static REFGroceryItemReference of(Long groceryItemId, Long majorId, Long minorId) {
        if (majorId == null || minorId == null) {
            throw new IllegalArgumentException("Category ID는 필수입니다.");
        }
        return new REFGroceryItemReference(groceryItemId, majorId, minorId);
    }
}
