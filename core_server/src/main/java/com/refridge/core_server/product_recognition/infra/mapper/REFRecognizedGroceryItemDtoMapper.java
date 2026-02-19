package com.refridge.core_server.product_recognition.infra.mapper;

import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemWithCategoryPathDto;
import com.refridge.core_server.product_recognition.infra.dto.REFRecognizedGroceryItemDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * GroceryItem Context의 DTO를 ProductRecognition Context의 DTO로 변환하는 Mapper.
 * Anti-Corruption Layer 역할 수행.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface REFRecognizedGroceryItemDtoMapper {
    REFRecognizedGroceryItemDto toRecognizedDto(REFGroceryItemWithCategoryPathDto dto);
}
