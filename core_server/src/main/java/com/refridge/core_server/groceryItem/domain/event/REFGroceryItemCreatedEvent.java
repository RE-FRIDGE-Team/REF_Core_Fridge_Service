package com.refridge.core_server.groceryItem.domain.event;

/**
 * GroceryItem이 신규로 생성되었을 때 발행되는 도메인 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   [LifeCycleService] createGroceryItem / createGroceryItemBulk / upsert
 *     ↓
 *   REFGroceryItem.create() → registerEvent(this event)
 *     ↓
 *   repository.save() / saveAll() — Spring Data JPA의 AbstractAggregateRoot
 *     메커니즘이 ApplicationEventPublisher로 전파
 *     ↓
 *   [Handler] REFGroceryItemMetadataAutoFillHandler
 *     → 카테고리 디폴트 프로필 + 특수 케이스 오버라이드 적용
 * </pre>
 *
 * <h3>왜 categoryId 두 개를 함께 전달하는가</h3>
 * <p>
 * 핸들러가 categoryName을 조회해서 프로필 매칭을 해야 하는데,
 * GroceryItem ID만 가지고 있으면 핸들러가 재차 DB를 조회해야 합니다.
 * 이벤트 페이로드에 category ID를 포함하면 핸들러는 ID → name 조회 1쿼리만 추가하면 됩니다.
 * </p>
 *
 * <h3>왜 categoryName이 아닌 categoryId를 보내는가</h3>
 * <p>
 * AR이 categoryName을 알려면 추가 의존성이 필요합니다 (categoryRepository).
 * AR을 인프라에 종속시키지 않기 위해 ID만 페이로드에 담고, 이름 조회는 핸들러 책임으로 둡니다.
 * </p>
 *
 * @param groceryItemId    신규 생성된 GroceryItem의 ID
 * @param groceryItemName  식재료 이름 (특수 케이스 resolver가 사용)
 * @param majorCategoryId  대분류 카테고리 ID
 * @param minorCategoryId  중분류 카테고리 ID
 *
 * @author 이승훈
 * @since 2026. 5. 14.
 */
public record REFGroceryItemCreatedEvent(
        Long groceryItemId,
        String groceryItemName,
        Long majorCategoryId,
        Long minorCategoryId
) {
}