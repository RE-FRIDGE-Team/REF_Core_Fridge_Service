package com.refridge.core_server.groceryItem.domain.vo;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRealProductName {
    private String productName;

    public static REFRealProductName of(String productName) {
       return new REFRealProductName(productName);
    }
}
