package com.refridge.core_server.recognition_feedback.domain.event;

/**
 * 식재료명 교정이 3중 게이트를 통과하여 확정되었을 때 발행되는 도메인 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   REFGroceryItemMappingHandler (recognition_feedback infra)
 *     → REFGroceryItemCorrectionService.confirmCorrection()
 *         → REFGroceryItemCorrectionConfirmedEvent 발행
 *             → REFGroceryItemCorrectionEventHandler (GroceryItem BC)
 *                   ① correctedName이 GroceryItem DB에 존재하는지 확인
 *                   ② 존재 → REFGroceryItemCorrectionAppliedEvent 발행 (originalProductName 포함)
 *                   ③ 없음 → NEW_GROCERY_ITEM 검수 큐 occurrenceCount 갱신
 * </pre>
 *
 * <h3>originalProductName 추가 이유</h3>
 * <p>
 * Product BC에서 upsert 시 {@code productName}에 실제 제품명이 들어가야
 * 다음 인식에서 {@code ProductIndexSearch}가 올바르게 매칭합니다.
 * {@code originalName}(식재료명)을 productName으로 사용하면
 * 실제 제품명 검색 시 매칭이 되지 않아 파이프라인 강화 목적이 달성되지 않습니다.
 * </p>
 *
 * @param originalName        파이프라인이 인식한 원본 식재료명 (집계 키, 로깅용)
 * @param correctedName       3중 게이트를 통과한 교정 식재료명
 * @param occurrenceCount     이 교정본의 선택 횟수
 * @param totalCount          전체 반응 횟수 (Gate 2 비율 기록용)
 * @param originalProductName 파이프라인이 인식한 원본 제품명 (Product upsert용, nullable)
 *                            null이면 GroceryItem BC가 originalName을 폴백으로 사용
 *
 * @author 이승훈
 * @since 2026. 4. 8. (수정: 2026. 4. 14.)
 */
public record REFGroceryItemCorrectionConfirmedEvent(
        String originalName,
        String correctedName,
        long occurrenceCount,
        long totalCount,
        String originalProductName
) {
}