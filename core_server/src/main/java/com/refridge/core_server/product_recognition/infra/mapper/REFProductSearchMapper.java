package com.refridge.core_server.product_recognition.infra.mapper;

import com.refridge.core_server.product.infra.dto.REFProductSearchResultDto;
import com.refridge.core_server.product_recognition.domain.dto.REFProductIndexSearchInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface REFProductSearchMapper {

    @Mapping(target = "matchType", constant = "EXACT")
    @Mapping(target = "similarityScore", constant = "1.0")
    @Mapping(source = "representativeImageUrl", target = "imageUrl")
    REFProductIndexSearchInfo toSearchInfo(REFProductSearchResultDto dto);
}
