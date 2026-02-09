package com.refridge.core_server.groceryItem.domain;

import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemDetailDTO;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemSummarizedDTO;

import java.util.List;
import java.util.Optional;

public interface REFGroceryItemRepositoryCustom {
    Optional<REFGroceryItemSummarizedDTO> findSummarizedDTOById(Long id);
    List<REFGroceryItemSummarizedDTO> findAllSummarizedDTOsByIds(List<Long> ids);
    Optional<REFGroceryItemSummarizedDTO> findSummarizedDTOByItemName(String groceryItemName);

    Optional<REFGroceryItemDetailDTO> findDetailDTOById(Long id);
    List<REFGroceryItemDetailDTO> findAllDetailDTOsByIds(List<Long> ids);
    Optional<REFGroceryItemDetailDTO> findDetailDTOByItemName(String groceryItemName);

}
