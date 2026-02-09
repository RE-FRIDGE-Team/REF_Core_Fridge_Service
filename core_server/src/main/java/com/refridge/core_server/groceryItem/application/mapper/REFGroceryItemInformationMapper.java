package com.refridge.core_server.groceryItem.application.mapper;

import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemDetailInfoResult;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemSummaryInfoResult;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemDetailDTO;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemSummarizedDTO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface REFGroceryItemInformationMapper {

    REFGroceryItemSummaryInfoResult toSummaryResult(REFGroceryItemSummarizedDTO refGroceryItemSummarizedDTO);

    List<REFGroceryItemSummaryInfoResult> toSummaryResultList(List<REFGroceryItemSummarizedDTO> refGroceryItemSummarizedDTOs);

    REFGroceryItemDetailInfoResult toDetailResult(REFGroceryItemDetailDTO refGroceryItemDetailDTO);

    List<REFGroceryItemDetailInfoResult> toDetailResultList(List<REFGroceryItemDetailDTO> refGroceryItemDetailDTOs);
}
