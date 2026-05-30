package com.refridge.core_server.recognition_feedback.infra.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Feedback Outbox 이벤트 리포지토리.
 *
 * <h2>MySQL SKIP LOCKED 전략</h2>
 * MySQL 8.0+에서 {@code SELECT ... FOR UPDATE SKIP LOCKED} 지원.
 * Hibernate에서 {@code @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")}로
 * SKIP LOCKED를 활성화한다 ({@code -2}가 SKIP LOCKED를 의미).
 *
 * <h2>왜 SKIP LOCKED인가</h2>
 * 다중 인스턴스 환경에서 한 row를 두 Relayer가 동시에 처리하지 않도록.
 * 일반 FOR UPDATE는 락 대기로 처리량 저하 → SKIP LOCKED는 잠긴 row 건너뛰고 다음 row 처리.
 *
 * <h2>MySQL 버전 요구</h2>
 * 8.0.1+ 필요. 5.7 이하면 {@code FOR UPDATE NOWAIT}로 대체하거나 단일 Relayer 운영.
 *
 * @author 승훈
 * @since 2026-05-18
 */
public interface REFFeedbackPendingEventRepository
        extends JpaRepository<REFFeedbackPendingEvent, String> {

    /**
     * PENDING 이벤트 배치 조회 (FOR UPDATE SKIP LOCKED).
     *
     * @param batchSize 한 번에 처리할 최대 이벤트 수
     * @return PENDING 상태 이벤트 목록
     */
    @Query(value = """
            SELECT e FROM REFFeedbackPendingEvent e
            WHERE e.status = 'PENDING'
            ORDER BY e.createdAt ASC
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<REFFeedbackPendingEvent> findPendingForUpdateSkipLocked(
            @Param("batchSize") int batchSize
    );

    /**
     * FAILED 이벤트 조회 (재시도 대상).
     */
    @Query("""
            SELECT e FROM REFFeedbackPendingEvent e
            WHERE e.status = 'FAILED'
              AND e.retryCount <= :maxRetryCount
            ORDER BY e.createdAt ASC
            """)
    List<REFFeedbackPendingEvent> findFailedForRetry(@Param("maxRetryCount") int maxRetryCount);

    long countByStatus(REFFeedbackPendingEvent.OutboxStatus status);
}