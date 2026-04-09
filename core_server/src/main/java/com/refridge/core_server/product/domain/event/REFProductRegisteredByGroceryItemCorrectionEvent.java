package com.refridge.core_server.product.domain.event;

/**
 * Product BC가 식재료명 교정 확정으로 인한 Product upsert를 완료했을 때
 * Feedback BC로 전파하는 도메인 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   [Product BC] REFProductRegistrationByGroceryItemCorrectionHandler
 *     → upsertProduct() 완료 후 이 이벤트 발행
 *     ↓
 *   [Feedback BC] REFGroceryItemCorrectionRegisteredHandler
 *     → feedback:registered:{originalName} 플래그 세팅
 *     → REFPositiveFeedbackAggregationHandler early return 활성화
 * </pre>
 *
 * <h3>originalName을 Feedback BC에 전달하는 이유</h3>
 * <p>
 * {@code feedback:registered} 플래그의 키는 {@code feedback:registered:{originalName}}입니다.
 * Feedback BC가 자신의 Redis 키 구조를 직접 관리할 수 있도록
 * Product BC는 originalName만 전달하고 Redis 세팅은 Feedback BC가 담당합니다.
 * </p>
 *
 * @param originalName 파이프라인이 인식한 원본 식재료명 (feedback:registered 키로 사용)
 * @param correctedName 교정된 식재료명 (로깅/추적용)
 * @param groceryItemId upsert된 Product에 연결된 GroceryItem ID
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 */
public record REFProductRegisteredByGroceryItemCorrectionEvent(
        String originalName,
        String correctedName,
        Long groceryItemId
) {
}