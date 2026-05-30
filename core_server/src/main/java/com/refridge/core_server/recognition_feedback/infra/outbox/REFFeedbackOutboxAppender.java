package com.refridge.core_server.recognition_feedback.infra.outbox;

import com.refridge.core_server.recognition_feedback.domain.event.REFCrossBoundaryFeedbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Feedback BC Outbox Appender.
 *
 * <h2>역할</h2>
 * {@link REFCrossBoundaryFeedbackEvent} 구현체가
 * {@code ApplicationEventPublisher.publishEvent()}로 발행되면
 * 이 컴포넌트가 {@code BEFORE_COMMIT} 단계에서 가로채
 * {@code feedback_pending_event} 테이블에 INSERT한다.
 *
 * <h2>BEFORE_COMMIT을 사용하는 이유</h2>
 * 동일 트랜잭션 안에서 INSERT가 일어나야 비즈니스 로직과 원자성 보장.
 * AFTER_COMMIT은 INSERT 실패 시 이벤트가 영구 유실됨.
 *
 * <h2>BC 내부 이벤트 제외</h2>
 * {@code REFPositiveFeedbackEvent}, {@code REFNegativeFeedbackEvent} 등 marker를 구현하지
 * 않는 이벤트는 자연스럽게 이 리스너에 잡히지 않는다 (인터페이스 타입 기반 dispatch).
 *
 * <h2>Jackson 3</h2>
 * {@code tools.jackson.core.JacksonException}은 unchecked.
 * BEFORE_COMMIT 안에서 던지면 트랜잭션 롤백되어 비즈니스 로직도 rollback —
 * 페이로드 직렬화 실패는 치명적이므로 의도된 동작.
 *
 * @author 승훈
 * @since 2026-05-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFFeedbackOutboxAppender {

    private final REFFeedbackPendingEventRepository repository;
    private final REFOutboxStreamKeyResolver streamKeyResolver;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCrossBoundary(REFCrossBoundaryFeedbackEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            String streamKey = streamKeyResolver.resolve(event.getClass());

            repository.save(REFFeedbackPendingEvent.create(
                    event.eventId(),
                    event.aggregateId(),
                    event.getClass().getSimpleName(),
                    streamKey,
                    json
            ));

            log.debug("[REFFeedbackOutboxAppender] Outbox INSERT 완료. eventType={}, streamKey={}, aggregateId={}",
                    event.getClass().getSimpleName(), streamKey, event.aggregateId());

        } catch (JacksonException e) {
            // BEFORE_COMMIT — 예외 전파로 트랜잭션 롤백
            log.error("[REFFeedbackOutboxAppender] 이벤트 직렬화 실패 — 트랜잭션 롤백. eventType={}, eventId={}",
                    event.getClass().getSimpleName(), event.eventId(), e);
            throw new OutboxSerializationException(
                    "Outbox 이벤트 직렬화 실패: " + event.getClass().getSimpleName(), e);
        } catch (IllegalArgumentException e) {
            // Stream Key 매핑 누락
            log.error("[REFFeedbackOutboxAppender] Stream Key 매핑 실패 — 트랜잭션 롤백. eventType={}",
                    event.getClass().getSimpleName(), e);
            throw e;
        } catch (Exception e) {
            log.error("[REFFeedbackOutboxAppender] Outbox INSERT 실패 — 트랜잭션 롤백. eventType={}, eventId={}",
                    event.getClass().getSimpleName(), event.eventId(), e);
            throw e;
        }
    }

    public static class OutboxSerializationException extends RuntimeException {
        public OutboxSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}