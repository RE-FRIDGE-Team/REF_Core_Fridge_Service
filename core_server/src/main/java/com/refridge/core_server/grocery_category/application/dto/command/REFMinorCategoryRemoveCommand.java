package com.refridge.core_server.grocery_category.application.dto.command;

import lombok.Builder;

@Builder
public record REFMinorCategoryRemoveCommand (
        Long majorCategoryId,
        Long minorCategoryId
) {
}
