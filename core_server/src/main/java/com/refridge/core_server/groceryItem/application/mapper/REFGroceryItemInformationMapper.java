package com.refridge.core_server.groceryItem.application.mapper;

import com.refridge.core_server.groceryItem.application.REFGroceryItemCategoricalService;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemDetailInfoResult;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemSummaryInfoResult;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemDetailDTO;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemSummarizedDTO;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring", uses = REFGroceryItemCategoricalService.class)
public interface REFGroceryItemInformationMapper {

    REFGroceryItemSummaryInfoResult toSummaryResult(REFGroceryItemSummarizedDTO refGroceryItemSummarizedDTO);

    List<REFGroceryItemSummaryInfoResult> toSummaryResultList(List<REFGroceryItemSummarizedDTO> refGroceryItemSummarizedDTOs);

    @Mapping(target = "categoryLabelColorCode", ignore = true)
    REFGroceryItemDetailInfoResult toDetailResult(REFGroceryItemDetailDTO dto);

    List<REFGroceryItemDetailInfoResult> toDetailResultList(List<REFGroceryItemDetailDTO> refGroceryItemDetailDTOs);

}
