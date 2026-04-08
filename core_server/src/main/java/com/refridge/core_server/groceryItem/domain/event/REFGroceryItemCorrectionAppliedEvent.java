package com.refridge.core_server.groceryItem.domain.event;

/**
 * GroceryItem BC가 식재료명 교정 확정 이벤트를 처리하여
 * 올바른 GroceryItem을 찾은 뒤 Product BC로 전파하는 도메인 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   [Feedback BC] REFGroceryItemCorrectionConfirmedEvent
 *     ↓
 *   [GroceryItem BC] REFGroceryItemCorrectionEventHandler
 *     → correctedName으로 GroceryItem 조회 성공 시 이 이벤트 발행
 *     ↓
 *   [Product BC] REFProductRegistrationByGroceryItemCorrectionHandler
 *     → upsertProduct()
 *     → REFProductRegisteredByGroceryItemCorrectionEvent 발행
 *     ↓
 *   [Feedback BC] REFGroceryItemCorrectionRegisteredHandler
 *     → feedback:registered:{originalName} 플래그 세팅
 * </pre>
 *
 * <h3>originalName 용도</h3>
 * <p>
 * 파이프라인이 인식한 원본 식재료명입니다.
 * Product BC에서 이 값을 제품명(productName)으로 사용하여 Product를 upsert합니다.
 * 이후 동일 제품 인식 시 ProductIndexSearch 단계에서 우선 매칭됩니다.
 * </p>
 *
 * @param originalName    파이프라인이 인식한 원본 식재료명 (Product의 productName으로 사용)
 * @param correctedName   교정된 식재료명 (GroceryItem 조회에 사용됨)
 * @param groceryItemId   correctedName에 해당하는 GroceryItem ID
 * @param majorCategoryId GroceryItem의 대분류 카테고리 ID
 * @param minorCategoryId GroceryItem의 중분류 카테고리 ID
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 */
public record REFGroceryItemCorrectionAppliedEvent(
        String originalName,
        String correctedName,
        Long groceryItemId,
        Long majorCategoryId,
        Long minorCategoryId
) {
}