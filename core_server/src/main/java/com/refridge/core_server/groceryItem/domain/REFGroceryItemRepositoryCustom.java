package com.refridge.core_server.groceryItem.domain;

import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemDetailDTO;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemSummarizedDTO;

import java.util.Optional;

public interface REFGroceryItemRepositoryCustom {
    Optional<REFGroceryItemSummarizedDTO> findSummarizedDTOById(Long id);
    Optional<REFGroceryItemSummarizedDTO> findSummarizedDTOByItemName(String groceryItemName);

    Optional<REFGroceryItemDetailDTO> findDetailDTOById(Long id);
    Optional<REFGroceryItemDetailDTO> findDetailDTOByItemName(String groceryItemName);
}
