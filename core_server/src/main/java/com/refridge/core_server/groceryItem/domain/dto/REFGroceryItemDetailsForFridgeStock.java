package com.refridge.core_server.groceryItem.domain.dto;

import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemClassification;
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemName;
import com.refridge.core_server.groceryItem.domain.vo.REFRepresentativeImage;
import lombok.Builder;

@Builder
public record REFGroceryItemDetailsForFridgeStock(Long id, String groceryItemName,
                                                  String representativeImageUrl, String groceryItemType, String matchedProductName) {

    public static REFGroceryItemDetailsForFridgeStock fromDomainVO(Long id, REFGroceryItemName groceryItemName,
                                                                   REFRepresentativeImage representativeImage, REFGroceryItemClassification groceryItemType,
                                                                   String matchedProductName) {
        return REFGroceryItemDetailsForFridgeStock.builder()
                .id(id)
                .groceryItemName(groceryItemName.getValue())
                .representativeImageUrl(representativeImage.getPresignedUrl())
                .groceryItemType(groceryItemType.getKorCode())
                .matchedProductName(matchedProductName)
                .build();
    }

}
