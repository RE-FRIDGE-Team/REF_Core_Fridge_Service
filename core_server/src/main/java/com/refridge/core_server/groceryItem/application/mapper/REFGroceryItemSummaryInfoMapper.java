package com.refridge.core_server.groceryItem.application.mapper;

import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemSummaryInfoResult;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemSummarizedDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface REFGroceryItemSummaryInfoMapper {

    REFGroceryItemSummaryInfoResult toResult(REFGroceryItemSummarizedDTO refGroceryItemSummarizedDTO);
}
