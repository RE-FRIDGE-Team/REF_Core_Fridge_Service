package com.refridge.core_server.grocery_category.domain;

import com.refridge.core_server.grocery_category.infra.persistence.dto.REFCategoryMetaDataWithCountRowDto;

import java.util.List;

public interface REFMajorGroceryCategoryRepositoryCustom {

    List<REFCategoryMetaDataWithCountRowDto> findAllCategoryHierarchyWithItemCount();

}
