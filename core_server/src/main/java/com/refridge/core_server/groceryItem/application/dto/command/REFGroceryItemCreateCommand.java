package com.refridge.core_server.groceryItem.application.dto.command;

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidateAndAdaptService;
import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import lombok.Builder;

@Builder
public record REFGroceryItemCreateCommand(
        String groceryItemName,
        String representativeImageUrl,
        String groceryItemClassification,
        Long majorCategoryId, Long minorCategoryId
) {
    public REFGroceryItem toEntity(REFGroceryItemCategoryValidateAndAdaptService categoryValidator) {
        return REFGroceryItem.create(
                groceryItemName,
                representativeImageUrl,
                groceryItemClassification,
                majorCategoryId,
                minorCategoryId,
                categoryValidator
        );
    }

    /**
     * CSV 부트스트랩 전용 팩토리 메서드.
     * - representativeImageUrl: 초기 데이터엔 없으므로 null
     * - groceryItemClassification: REFInventoryItemType → name() 변환
     */
    public static REFGroceryItemCreateCommand forBootstrap(
            String groceryItemName,
            Long majorCategoryId,
            Long minorCategoryId,
            REFInventoryItemType type
    ) {
        return REFGroceryItemCreateCommand.builder()
                .groceryItemName(groceryItemName)
                .representativeImageUrl(null)
                .groceryItemClassification(type.name())
                .majorCategoryId(majorCategoryId)
                .minorCategoryId(minorCategoryId)
                .build();
    }
}
