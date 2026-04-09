package com.refridge.core_server.groceryItem.domain.event;

/**
 * GroceryItem BC가 카테고리 재분류 승인을 처리하여
 * GroceryItem 카테고리 변경을 완료했을 때 Product BC로 전파하는 도메인 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   [Feedback BC] REFCategoryReassignmentApprovedEvent
 *     ↓
 *   [GroceryItem BC] REFGroceryItemCategoryReassignmentEventHandler
 *     → targetValue 파싱 → 카테고리 ID 조회 → GroceryItem 카테고리 변경
 *     → 이 이벤트 발행
 *     ↓
 *   [Product BC] REFProductCategoryUpdateByReassignmentHandler
 *     → origProductName으로 Product 존재 확인
 *     → 존재 O → updateCategoryReference()
 *     → 존재 X → upsertProduct()
 * </pre>
 *
 * <h3>origProductName / origBrandName 전달 이유</h3>
 * <p>
 * Product BC 핸들러가 해당 원본 제품명으로 등록된 Product 존재 여부를 확인하는 데 사용합니다.
 * GroceryItem BC는 Product BC 구조를 알지 못하므로 Feedback BC로부터 받은 값을
 * 그대로 전달합니다.
 * </p>
 *
 * @param groceryItemId      카테고리가 변경된 GroceryItem ID
 * @param groceryItemName    GroceryItem 이름 (로깅/추적용)
 * @param newMajorCategoryId 변경된 대분류 카테고리 ID
 * @param newMinorCategoryId 변경된 중분류 카테고리 ID
 * @param origProductName    원본 인식 제품명 (Product 존재 여부 확인용, nullable)
 * @param origBrandName      원본 브랜드명 (신규 Product 생성 시 사용, nullable)
 * @param sourceFeedbackId   최초 발생 피드백 ID (역추적용)
 *
 * @author 이승훈
 * @since 2026. 4. 9.
 */
public record REFGroceryItemCategoryChangedEvent(
        Long groceryItemId,
        String groceryItemName,
        Long newMajorCategoryId,
        Long newMinorCategoryId,
        String origProductName,
        String origBrandName,
        java.util.UUID sourceFeedbackId
) {
}