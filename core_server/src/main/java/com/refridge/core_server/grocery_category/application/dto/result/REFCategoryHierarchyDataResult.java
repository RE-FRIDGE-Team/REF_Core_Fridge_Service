package com.refridge.core_server.grocery_category.application.dto.result;

import lombok.Builder;

import java.util.List;

@Builder
public record REFCategoryHierarchyDataResult (
        List<MajorCategoryWithCount> majorCategories
) {

    @Builder
    public record MajorCategoryWithCount(
            Long majorCategoryId,
            String majorCategoryName,
            String colorTagHexCode,
            long totalItemCount,
            List<MinorCategoryWithCount> minorCategories
    ) {}


    @Builder
    public record MinorCategoryWithCount(
            Long minorCategoryId,
            String minorCategoryName,
            long itemCount
    ) {}

}
