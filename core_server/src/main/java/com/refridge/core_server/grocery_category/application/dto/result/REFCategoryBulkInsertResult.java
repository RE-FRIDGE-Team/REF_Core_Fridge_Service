package com.refridge.core_server.grocery_category.application.dto.result;

import lombok.Builder;

import java.util.List;

/**
 * RE:FRIDGE의 식료품 카테고리를 <b><font color="red">대량으로 추가</font></b>하는 서비스레벨 결과 DTO입니다.<p>
 * 주로 {@link com.refridge.core_server.grocery_category.application.REFCategoryLifeCycleService}에서 사용합니다.<p>
 * 초기 카테고리 자동 생성을 위해 사용되며, 대분류 카테고리와 해당 대분류 카테고리에 속하는 중분류 카테고리를 함께 추가하는 형태로 사용됩니다.<p>
 * @param majorCategoryCreationResult
 * @param minorCategoryCreationResult
 */
// TODO : 초기 카테고리 자동생성 Initializer 구현 해야 함.
@Builder
public record REFCategoryBulkInsertResult(
        REFMajorCategoryCreationResult majorCategoryCreationResult,
        List<REFMinorCategoryCreationResult> minorCategoryCreationResult
) {
}
