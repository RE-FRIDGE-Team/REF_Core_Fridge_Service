package com.refridge.core_server.grocery_category.domain.vo;

/**
 * 중분류 카테고리 생성 시 이름과 아이템 타입을 함께 전달하기 위한 도메인 레코드.
 * Application 레이어의 Command와 도메인 레이어 사이에서 데이터를 전달하는 역할을 한다.
 */
public record REFMinorCategoryCreationData(
        String name,
        REFInventoryItemType itemType
) {
}