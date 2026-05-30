package com.refridge.core_server.recognition_feedback.infra.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Feedback BC Outbox Relayer.
 *
 * <h2>역할</h2>
 * {@code feedback_pending_event} 테이블에서 PENDING 이벤트를 폴링해
 * Redis Stream에 XADD로 발행한다.
 *
 * <h2>동시성 제어</h2>
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}로 다중 인스턴스 환경에서
 * 동일 row 중복 처리를 방지한다.
 *
 * <h2>재시도 정책</h2>
 * 발행 실패 시 status=FAILED, retry_count 증가.
 * {@code retryFailed}가 1분마다 FAILED를 PENDING으로 리셋하여 재처리.
 * 최대 {@code MAX_RETRY_COUNT}회까지만 재시도.
 *
 * <h2>메트릭</h2>
 * <ul>
 *   <li>{@code feedback.outbox.publish.success}</li>
 *   <li>{@code feedback.outbox.publish.failure}</li>
 * </ul>
 *
 * @author 승훈
 * @since 2026-05-18
 */
@Slf4j
@Component
public class REFFeedbackOutboxRelayer {

    private static final int BATCH_SIZE      = 100;
    private static final int MAX_RETRY_COUNT = 5;

    private final REFFeedbackPendingEventRepository repository;
    private final REFFeedbackStreamPublisher publisher;
    private final Counter successCounter;
    private final Counter failureCounter;

    public REFFeedbackOutboxRelayer(
            REFFeedbackPendingEventRepository repository,
            REFFeedbackStreamPublisher publisher,
            MeterRegistry meterRegistry
    ) {
        this.repository     = repository;
        this.publisher      = publisher;
        this.successCounter = meterRegistry.counter("feedback.outbox.publish.success");
        this.failureCounter = meterRegistry.counter("feedback.outbox.publish.failure");
    }

    /**
     * PENDING 이벤트 발행 (2초 간격).
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay() {
        List<REFFeedbackPendingEvent> batch =
                repository.findPendingForUpdateSkipLocked(BATCH_SIZE);

        if (batch.isEmpty()) return;

        log.debug("[REFFeedbackOutboxRelayer] 발행 대상 {}건 처리 시작", batch.size());

        for (REFFeedbackPendingEvent event : batch) {
            try {
                publisher.publish(event.getStreamKey(), event.getPayload());
                event.markPublished();
                successCounter.increment();
            } catch (Exception ex) {
                log.error("[REFFeedbackOutboxRelayer] 발행 실패. eventId={}, eventType={}, error={}",
                        event.getEventId(), event.getEventType(), ex.getMessage());
                event.markFailed(ex.getMessage());
                failureCounter.increment();
            }
        }

        repository.saveAll(batch);
    }

    /**
     * FAILED 이벤트 재시도 (1분 간격).
     * MAX_RETRY_COUNT 이하인 이벤트만 PENDING으로 리셋.
     * 초과 이벤트는 운영팀 수동 확인 대상.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void retryFailed() {
        List<REFFeedbackPendingEvent> failedBatch =
                repository.findFailedForRetry(MAX_RETRY_COUNT);

        if (failedBatch.isEmpty()) return;

        log.warn("[REFFeedbackOutboxRelayer] FAILED 이벤트 재시도. {}건", failedBatch.size());

        for (REFFeedbackPendingEvent event : failedBatch) {
            event.resetToPending();
        }

        repository.saveAll(failedBatch);
    }
}