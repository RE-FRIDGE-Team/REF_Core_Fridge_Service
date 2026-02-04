package com.refridge.core_server.grocery_category.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryCategoryName {

    @Column(name = "category_name")
    private String value;

    public static REFGroceryCategoryName of(String value) {
        return new REFGroceryCategoryName(value);
    }

}
