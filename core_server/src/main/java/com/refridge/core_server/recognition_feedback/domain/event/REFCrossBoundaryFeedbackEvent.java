package com.refridge.core_server.recognition_feedback.domain.event;

import java.time.Instant;

/**
 * Feedback BC 외부에서 소비되는 이벤트 마커 인터페이스.
 *
 * <h2>역할</h2>
 * Outbox Appender가 이 인터페이스로 분기하여 적재 대상을 식별한다.
 * BC 내부 이벤트({@code REFPositiveFeedbackEvent}, {@code REFNegativeFeedbackEvent})는
 * 이 인터페이스를 구현하지 않으므로 Outbox에 적재되지 않는다.
 *
 * <h2>구현체 (3종)</h2>
 * <ul>
 *   <li>{@code REFProductFeedbackAggregationEvent} — Product BC가 소비</li>
 *   <li>{@code REFCategoryReassignmentApprovedEvent} — GroceryItem + Product BC가 소비</li>
 *   <li>{@code REFGroceryItemCorrectionConfirmedEvent} — GroceryItem BC가 소비</li>
 * </ul>
 *
 * <h2>비구현체 (BC 내부 이벤트, Outbox 적재 대상 아님)</h2>
 * <ul>
 *   <li>{@code REFPositiveFeedbackEvent}</li>
 *   <li>{@code REFNegativeFeedbackEvent}</li>
 *   <li>{@code REFDictionarySyncedEvent}</li>
 * </ul>
 *
 * <h2>왜 메서드 3개를 강제하는가</h2>
 * <ul>
 *   <li>{@link #eventId} — Outbox row의 primary key</li>
 *   <li>{@link #aggregateId} — Outbox row의 aggregate_id 컬럼.
 *       이 메서드를 강제하면 Appender에서 {@code extractAggregateId(event)} switch 분기가 불필요해진다.</li>
 *   <li>{@link #occurredAt} — Outbox row의 created_at 보정용 (현재 시각과 다를 수 있음)</li>
 * </ul>
 *
 * <h2>트레이드오프</h2>
 * marker interface가 도메인 이벤트에 인프라 관심사를 끌어들인다는 비판이 가능하다.
 * 대안: (i) listener 측이 instanceof 분기, (ii) class 이름 화이트리스트.
 * marker가 가장 명시적이라 채택. 도메인 record가 어차피 eventId/occurredAt을 갖고 있어
 * 추가 부담은 {@code aggregateId()} 한 메서드뿐.
 *
 * @author 승훈
 * @since 2026-05-18
 */
public interface REFCrossBoundaryFeedbackEvent {

    /** 이벤트 고유 ID (UUID 권장) — Outbox row의 PK */
    String eventId();

    /** AR 식별자 (recognitionId / feedbackId / reviewId 등) — Outbox row의 aggregate_id */
    String aggregateId();

    /** 이벤트 발생 시각 — Outbox row의 created_at 보정용 */
    Instant occurredAt();
}