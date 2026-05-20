package com.refridge.core_server.groceryItem.infra.persistence.dto;

/**
 * UsageProfile 부트스트랩용 단건 조회 DTO입니다.
 *
 * <h3>용도</h3>
 * <p>
 * 부트스트랩 Runner가 각 GroceryItem에 대해 minor/major categoryName과 품목명을
 * 알아야 프로필 적용 + 특수 케이스 오버라이드를 수행할 수 있습니다.
 * 이를 단일 JOIN 쿼리로 가져오기 위한 projection 결과.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
public record REFGroceryItemBootstrapDTO(
        Long groceryItemId,
        String groceryItemName,
        String minorCategoryName,
        String majorCategoryName
) {
}