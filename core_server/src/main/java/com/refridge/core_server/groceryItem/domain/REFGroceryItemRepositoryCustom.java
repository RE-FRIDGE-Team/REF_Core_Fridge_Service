package com.refridge.core_server.groceryItem.domain;

import com.refridge.core_server.groceryItem.infra.persistence.dto.*;

import java.util.List;
import java.util.Optional;

public interface REFGroceryItemRepositoryCustom {
    Optional<REFGroceryItemSummarizedDTO> findSummarizedDTOById(Long id);
    List<REFGroceryItemSummarizedDTO> findAllSummarizedDTOsByIds(List<Long> ids);

    Optional<REFGroceryItemDetailDTO> findDetailDTOById(Long id);
    List<REFGroceryItemDetailDTO> findAllDetailDTOsByIds(List<Long> ids);

    Optional<REFGroceryItemWithCategoryPathDto> findByGroceryItemName(String groceryItemName);
    Optional<REFGroceryItemForUpsertDto> findForUpsertByGroceryItemName(String groceryItemName);

    /**
     * 식재료 ID 목록으로 REFInventoryItemType 배치 조회.
     * CSV 내보내기 시 카테고리태그 컬럼 채우는 용도.
     * GroceryItem → MinorCategory JOIN으로 itemType 획득.
     */
    List<REFGroceryItemItemTypeDto> findItemTypesByIds(List<Long> ids);
}
