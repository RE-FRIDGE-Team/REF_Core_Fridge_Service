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

    List<REFGroceryItemItemTypeDto> findItemTypesByIds(List<Long> ids);

    /**
     * fridge_server 노출용 GroceryItem 메타데이터 단건 조회.
     * GroceryItem → MinorCategory JOIN으로 itemType까지 한 번에 가져옵니다.
     */
    Optional<REFGroceryItemMetadataDTO> findMetadataDTOById(Long id);

    /**
     * 부트스트랩용 — 커서 기반 ID 페이징 조회 (ID만 반환).
     */
    List<Long> findActiveIdsAfter(Long afterId, int pageSize);

    /**
     * 부트스트랩용 — 페이지 단위로 GroceryItem ID/이름/minor·major categoryName을 함께 가져옵니다.
     * UsageProfile 적용 + SpecialCaseResolver 호출에 필요한 모든 정보를 1쿼리로 묶습니다.
     */
    List<REFGroceryItemBootstrapDTO> findBootstrapPageAfter(Long afterId, int pageSize);
}