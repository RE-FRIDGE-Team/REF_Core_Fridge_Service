package com.refridge.core_server.grocery_category.application.dto.result;

import lombok.Builder;

import java.util.List;

@Builder
public record REFCategoryBulkInsertResult(
        REFMajorCategoryCreationResult majorCategoryCreationResult,
        List<REFMinorCategoryCreationResult> minorCategoryCreationResult
) {
}
