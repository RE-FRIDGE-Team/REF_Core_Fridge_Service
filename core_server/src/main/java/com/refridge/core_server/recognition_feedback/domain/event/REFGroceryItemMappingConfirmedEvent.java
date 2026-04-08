package com.refridge.core_server.recognition_feedback.domain.event;

/**
 * GroceryItem 매핑이 3중 게이트를 통과하여 확정되었을 때 발행되는 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   REFGroceryItemMappingHandler
 *     → REFGroceryItemMappingConfirmationService.confirmMapping()
 *         → REFGroceryItemMappingConfirmedEvent 발행
 *             → REFGroceryItemMappingConfirmedEventHandler (Phase 4)
 *                   ① correctedGroceryItemName DB 존재 확인
 *                   ② 존재 → Product upsert (ProductIndexSearch에서 이후 매칭)
 *                   ③ 없음 → NEW_GROCERY_ITEM 검수 큐 occurrenceCount 갱신
 * </pre>
 *
 * @param originalGroceryItemName  파이프라인이 인식한 원본 식재료명
 * @param correctedGroceryItemName 3중 게이트를 통과한 수정 식재료명
 * @param occurrenceCount          이 수정본의 선택 횟수
 * @param totalCount               전체 반응 횟수 (Gate 2 기록용)
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 */
public record REFGroceryItemMappingConfirmedEvent(
        String originalGroceryItemName,
        String correctedGroceryItemName,
        long occurrenceCount,
        long totalCount
) {
}