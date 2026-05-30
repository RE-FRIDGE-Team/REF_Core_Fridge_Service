package com.refridge.core_server.recognition_feedback.infra.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Feedback BC Outbox 이벤트 엔티티.
 *
 * <h2>역할</h2>
 * BC 외부로 발행되어야 하는 도메인 이벤트({@code REFCrossBoundaryFeedbackEvent} 구현체)를
 * Redis Stream으로 발행하기 전 MySQL 테이블에 먼저 저장한다.
 * 비즈니스 트랜잭션과 이벤트 발행의 원자성을 보장한다.
 *
 * <h2>fridge_server FridgePendingEvent와의 차이</h2>
 * <ul>
 *   <li>DB: PostgreSQL → MySQL (DDL 차이 — JSON 타입, DATETIME(6))</li>
 *   <li>{@code aggregate_type} 항상 {@code "FEEDBACK"}</li>
 *   <li>대상 이벤트는 3종 ({@code REFCrossBoundaryFeedbackEvent} 구현체)</li>
 * </ul>
 *
 * <h2>상태 전이</h2>
 * <pre>
 *   PENDING → PUBLISHED   (정상 발행)
 *   PENDING → FAILED      (발행 실패, retry_count 증가)
 *   FAILED  → PENDING     (재시도)
 * </pre>
 *
 * <h2>JSON 컬럼</h2>
 * MySQL 5.7+ JSON 타입. Hibernate {@code @JdbcTypeCode(SqlTypes.JSON)}로 매핑.
 * String을 그대로 저장하면 MySQL이 JSON validation 수행.
 *
 * <h2>DDL</h2>
 * Flyway 미적용 환경이므로 별도 SQL 스크립트로 적용 (산출물 _DDL_INSTRUCTIONS.sql 참고).
 *
 * @author 승훈
 * @since 2026-05-18
 */
@Entity
@Table(
        name = "feedback_pending_event",
        indexes = @Index(name = "idx_feedback_pending", columnList = "status, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFFeedbackPendingEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    /** 항상 'FEEDBACK' */
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 40)
    private String aggregateType;

    /** recognitionId / feedbackId / reviewId 등 */
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 80)
    private String aggregateId;

    /** 이벤트 클래스 단순명 (ex: 'REFGroceryItemCorrectionConfirmedEvent') */
    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    /** Redis Stream key (ex: 'feedback:grocery-item-correction-confirmed') */
    @Column(name = "stream_key", nullable = false, updatable = false, length = 80)
    private String streamKey;

    /**
     * 이벤트 record를 JSON 직렬화한 페이로드.
     * MySQL JSON 타입 사용 — String으로 매핑하되 Hibernate가 JSON 컬럼임을 인식.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    // ── 팩토리 ────────────────────────────────────────────────────────

    public static REFFeedbackPendingEvent create(
            String eventId,
            String aggregateId,
            String eventType,
            String streamKey,
            String payload
    ) {
        REFFeedbackPendingEvent e = new REFFeedbackPendingEvent();
        e.eventId       = Objects.requireNonNull(eventId, "eventId");
        e.aggregateType = "FEEDBACK";
        e.aggregateId   = Objects.requireNonNull(aggregateId, "aggregateId");
        e.eventType     = Objects.requireNonNull(eventType, "eventType");
        e.streamKey     = Objects.requireNonNull(streamKey, "streamKey");
        e.payload       = Objects.requireNonNull(payload, "payload");
        e.status        = OutboxStatus.PENDING;
        e.retryCount    = 0;
        e.createdAt     = Instant.now();
        return e;
    }

    // ── 상태 전이 ─────────────────────────────────────────────────────

    public void markPublished() {
        this.status      = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError   = null;
    }

    public void markFailed(String errorMessage) {
        this.status     = OutboxStatus.FAILED;
        this.retryCount = this.retryCount + 1;
        this.lastError  = (errorMessage != null && errorMessage.length() > 500)
                ? errorMessage.substring(0, 500)
                : errorMessage;
    }

    public void resetToPending() {
        this.status    = OutboxStatus.PENDING;
        this.lastError = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof REFFeedbackPendingEvent p)) return false;
        return Objects.equals(eventId, p.eventId);
    }

    @Override
    public int hashCode() { return Objects.hash(eventId); }

    /** Outbox 이벤트 처리 상태 */
    public enum OutboxStatus {
        PENDING, PUBLISHED, FAILED
    }
}