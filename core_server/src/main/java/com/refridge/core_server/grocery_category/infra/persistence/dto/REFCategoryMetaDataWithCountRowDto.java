package com.refridge.core_server.grocery_category.infra.persistence.dto;

import com.querydsl.core.annotations.QueryProjection;

public record REFCategoryMetaDataWithCountRowDto(
        Long majorCategoryId,
        String majorCategoryName,
        Long minorCategoryId,
        String minorCategoryName,
        long itemCount
) {

    @QueryProjection
    public REFCategoryMetaDataWithCountRowDto {}

}
