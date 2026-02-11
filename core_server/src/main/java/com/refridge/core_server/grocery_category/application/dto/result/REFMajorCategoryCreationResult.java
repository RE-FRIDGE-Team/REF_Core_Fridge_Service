package com.refridge.core_server.grocery_category.application.dto.result;

import lombok.Builder;

@Builder
public record REFMajorCategoryCreationResult(
        Long createdMajorCategoryId) {
}
