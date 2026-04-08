package com.refridge.core_server.recognition_feedback.domain.event;

/**
 * 식재료명 교정이 3중 게이트를 통과하여 확정되었을 때 발행되는 도메인 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   REFGroceryItemMappingHandler (recognition_feedback infra)
 *     → REFGroceryItemCorrectionService.confirmCorrection()
 *         → REFGroceryItemCorrectionConfirmedEvent 발행
 *             → REFGroceryItemCorrectionConfirmedEventHandler (Phase 4)
 *                   ① correctedName이 GroceryItem DB에 존재하는지 확인
 *                   ② 존재 → Product upsert (다음 인식 시 ProductIndexSearch에서 우선 매칭)
 *                   ③ 없음 → NEW_GROCERY_ITEM 검수 큐 occurrenceCount 갱신
 * </pre>
 *
 * <h3>correctedName 신뢰도</h3>
 * <p>
 * 이 이벤트가 발행되는 시점에 {@code correctedName}은 3중 게이트(횟수/비율/우위)를
 * 모두 통과한 상태입니다. Phase 4 핸들러는 DB 존재 여부만 확인하면 됩니다.
 * </p>
 *
 * @param originalName    파이프라인이 인식한 원본 식재료명
 * @param correctedName   3중 게이트를 통과한 교정 식재료명
 * @param occurrenceCount 이 교정본의 선택 횟수
 * @param totalCount      전체 반응 횟수 (Gate 2 비율 기록용)
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 */
public record REFGroceryItemCorrectionConfirmedEvent(
        String originalName,
        String correctedName,
        long occurrenceCount,
        long totalCount
) {
}
