package com.refridge.core_server.recognition_feedback.domain.event;

import java.time.Instant;
import java.util.UUID;

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
 * <h3>Cross-Boundary 이벤트 — Step 7 Outbox 적용 대상 (F3)</h3>
 * <p>
 * 이 이벤트는 Feedback BC에서 발행되어 GroceryItem BC가 구독하는
 * <b>BC 경계 외부 이벤트</b>입니다.
 * {@link REFCrossBoundaryFeedbackEvent} 마커를 구현하여
 * {@code REFFeedbackOutboxAppender}가 @TransactionalEventListener(BEFORE_COMMIT) 단계에서
 * 자동으로 식별·적재하도록 합니다.
 * </p>
 * <p>
 * Outbox 적재 → MySQL feedback_pending_event INSERT → Relayer가 XADD로
 * {@code feedback:grocery-item-correction-confirmed} 스트림에 발행합니다.
 * </p>
 *
 * <h3>aggregateId 선정 근거</h3>
 * <p>
 * 이 이벤트의 자연 키는 {@code originalName}입니다. 같은 originalName에 대한
 * 교정 확정 이벤트가 두 번 들어오면 GroceryItem BC가 동일한 처리를 수행해야 하므로
 * 멱등성 추적의 키로 적합합니다.
 * </p>
 *
 * @param eventId             이벤트 고유 ID (UUID 문자열) — Outbox row PK, 멱등성 키
 * @param aggregateId         AR 식별자 — {@code originalName} (교정 대상의 자연 키)
 * @param occurredAt          이벤트 발생 시각
 * @param originalName        파이프라인이 인식한 원본 식재료명 (집계 키, 로깅용)
 * @param correctedName       3중 게이트를 통과한 교정 식재료명
 * @param occurrenceCount     이 교정본의 선택 횟수
 * @param totalCount          전체 반응 횟수 (Gate 2 비율 기록용)
 * @param originalProductName 파이프라인이 인식한 원본 제품명 (Product upsert용, nullable)
 *                            null이면 GroceryItem BC가 originalName을 폴백으로 사용
 *
 * @author 이승훈
 * @since 2026. 4. 8. (Step 7 marker 적용: 2026. 5. 30.)
 */
public record REFGroceryItemCorrectionConfirmedEvent(
        String eventId,
        String aggregateId,
        Instant occurredAt,
        String originalName,
        String correctedName,
        long occurrenceCount,
        long totalCount,
        String originalProductName
) implements REFCrossBoundaryFeedbackEvent {

    /**
     * 발행 측이 기존 시그니처처럼 호출할 수 있도록 제공하는 정적 팩토리.
     * <ul>
     *   <li>{@code eventId} — 새 UUID 자동 생성</li>
     *   <li>{@code aggregateId} — {@code originalName} (교정 대상의 자연 키)</li>
     *   <li>{@code occurredAt} — 호출 시점 ({@link Instant#now})</li>
     * </ul>
     */
    public static REFGroceryItemCorrectionConfirmedEvent of(
            String originalName,
            String correctedName,
            long occurrenceCount,
            long totalCount,
            String originalProductName
    ) {
        return new REFGroceryItemCorrectionConfirmedEvent(
                UUID.randomUUID().toString(),
                originalName,
                Instant.now(),
                originalName,
                correctedName,
                occurrenceCount,
                totalCount,
                originalProductName
        );
    }
}