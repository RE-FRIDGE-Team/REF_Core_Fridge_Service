package com.refridge.core_server.grocery_category.application.dto.result;

import lombok.Builder;

import java.util.List;

/**
 * RE:FRIDGE의 식료품 카테고리 계층 구조 메타데이터 조회 결과 DTO입니다.<p>
 * 대분류 및 중분류에 대한 기본 메타데이터와 함께 분류 속 유니크한 식재료 수를 포함하여 반환합니다.<p>
 * @param majorCategories 대분류 메타데이터
 */
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
