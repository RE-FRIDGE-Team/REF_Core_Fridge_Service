package com.refridge.core_server.groceryItem.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryItemName {

    @Column(name = "item_name")
    private String value;

    public static REFGroceryItemName of(String value) {
        return new REFGroceryItemName(value);
    }
}
