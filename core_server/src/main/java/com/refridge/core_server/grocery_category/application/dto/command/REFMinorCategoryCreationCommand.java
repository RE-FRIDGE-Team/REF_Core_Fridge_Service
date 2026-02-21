package com.refridge.core_server.grocery_category.application.dto.command;

import lombok.Builder;

/**
 * RE:FRIDGE의 식료품 카테고리 중 중분류 카테고리를 <font color="blue"><b>생성</b></font>하는 서비스레벨 커맨드입니다.<p>
 * 중분류 카테고리는 식료품 카테고리의 2번째 계층으로, 예를 들어 "과일/견과류" 대분류 아래에 "과일", "견과류", "냉동 과일" 등의 중분류가 있습니다.<p><p>
 * 주로 {@link com.refridge.core_server.grocery_category.application.REFCategoryLifeCycleService}에서 사용합니다.<p>
 * <b>⚠️CAUTION : 반드시 대분류 카테고리에 대한 정보가 포함되어야 하며, 해당 대분류 카테고리에 중분류 카테고리를 추가하는 형태로 사용됩니다.</b><p>
 *
 * 카테고리 분류 문서:
 * <a href="https://github.com/RE-FRIDGE-Team/REF_Classification_For_Ingredient_Recognition/blob/main/processed_dataset/supervised_learning_dataset/refridge_processed_category_data_set.json">
 * 카테고리 목록
 * </a>
 * @param minorCategoryName
 * @param majorCategoryId
 */
@Builder
public record REFMinorCategoryCreationCommand (
        String minorCategoryName,
        Long majorCategoryId
) {
}
