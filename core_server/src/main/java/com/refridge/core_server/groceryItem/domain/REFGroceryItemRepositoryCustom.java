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

    /**
     * fridge_server 노출용 GroceryItem 메타데이터 단건 조회.
     * <p>
     * GroceryItem → MinorCategory JOIN으로 itemType까지 한 번에 가져옵니다.
     * @Embedded VO 필드(usageMetadata.*)는 같은 row에서 조회되므로 추가 JOIN 없습니다.
     * </p>
     *
     * @param id GroceryItem ID
     * @return 메타데이터 DTO (ACTIVE 상태인 항목만)
     */
    Optional<REFGroceryItemMetadataDTO> findMetadataDTOById(Long id);

    /**
     * 부트스트랩(카테고리 디폴트 프로필 일괄 적용)용 ID 페이징 조회.
     * <p>
     * GroceryItem 전체를 메모리에 올리지 않기 위해 페이징됩니다.
     * 호출자(ApplicationRunner)는 이 ID들을 받아 청크 단위로
     * {@code findById}와 {@code applyDefaultProfile}을 수행합니다.
     * </p>
     *
     * @param afterId  이 ID 이후 항목만 (커서 기반 페이징; 첫 호출 시 0L)
     * @param pageSize 한 번에 가져올 개수
     * @return ACTIVE 상태인 GroceryItem ID 목록 (id 오름차순)
     */
    List<Long> findActiveIdsAfter(Long afterId, int pageSize);
}