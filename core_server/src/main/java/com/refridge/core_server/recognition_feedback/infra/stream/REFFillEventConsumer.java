package com.refridge.core_server.recognition_feedback.infra.stream;

import com.refridge.core_server.recognition_feedback.application.REFFeedbackLifeCycleService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * fridge_server가 발행한 {@code fridge:fill-completed} 스트림을 구독하는 컨슈머.
 *
 * <h2>처리 흐름</h2>
 * <pre>
 *   메시지 수신
 *     ↓
 *   payload JSON → REFFillEventPayload deserialize
 *     ↓
 *   recognitionId null 검증 → null이면 ACK 후 스킵
 *     ↓
 *   userEditedFields 비어있나?
 *     ├─ YES → approveFeedback(command)
 *     └─ NO  → correctFeedback(command)
 *     ↓
 *   ACK
 *
 *   예외 처리:
 *     - IllegalStateException (이미 처리됨/PENDING이 아님) → 정상 멱등 흐름, ACK + 메트릭
 *     - 그 외 예외 → ACK 안 함 (XPENDING에 남고 재시도)
 *     - maxDeliveryCount 초과 → dead-letter 스트림으로 이동
 * </pre>
 *
 * <h2>Jackson 3 사용</h2>
 * {@code tools.jackson.databind.ObjectMapper}를 사용. Jackson 2는 사용하지 않는다.
 * core_server 전역 {@code ObjectMapper} 빈 (Spring Boot 4 자동 설정)을 주입받는다.
 *
 * <h2>멱등성</h2>
 * <ul>
 *   <li>{@code REFRecognitionFeedback.approve()}는 PENDING이 아니면 {@code IllegalStateException}.</li>
 *   <li>같은 recognitionId로 두 번째 호출이 오면 자연스럽게 catch → ACK.</li>
 *   <li>{@code findOrCreate} 보상 패턴이 누락된 PENDING도 자동 생성.</li>
 * </ul>
 *
 * <h2>at-least-once 보장</h2>
 * Redis Stream + Consumer Group은 at-least-once. 같은 메시지가 두 번 들어와도
 * Feedback BC가 멱등하므로 안전. 다른 인스턴스가 동시에 처리할 수도 있는데,
 * core_server의 DB 트랜잭션(낙관적 락 + PENDING 상태 가드)이 한쪽만 성공시킨다.
 *
 * @author 승훈
 * @since 2026-05-18
 */
@Slf4j
@Component
public class REFFillEventConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    public static final String STREAM_KEY = "fridge:fill-completed";
    public static final String GROUP_NAME = "feedback-fill-group";

    /** Dead-letter 스트림 키 — maxDeliveryCount 초과 메시지가 이동 */
    public static final String DEAD_LETTER_STREAM = "dead-letter:fill-completed";

    /** 메시지당 최대 재시도 횟수 — 초과 시 dead-letter로 */
    private static final int MAX_DELIVERY_COUNT = 5;

    private static final String PAYLOAD_FIELD = "payload";

    private final REFFeedbackLifeCycleService feedbackLifeCycleService;
    private final REFFillEventCommandMapper commandMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;

    public REFFillEventConsumer(
            REFFeedbackLifeCycleService feedbackLifeCycleService,
            REFFillEventCommandMapper commandMapper,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry
    ) {
        this.feedbackLifeCycleService = feedbackLifeCycleService;
        this.commandMapper            = commandMapper;
        this.objectMapper             = objectMapper;
        this.redisTemplate            = redisTemplate;

        this.processedCounter  = meterRegistry.counter("feedback.fill.consumer.processed");
        this.duplicateCounter  = meterRegistry.counter("feedback.fill.consumer.duplicate");
        this.failedCounter     = meterRegistry.counter("feedback.fill.consumer.failed");
        this.deadLetterCounter = meterRegistry.counter("feedback.fill.consumer.dead_letter");
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String payloadJson = message.getValue().get(PAYLOAD_FIELD);

        if (payloadJson == null) {
            log.warn("[FillEventConsumer] payload 필드 없음 — ACK 후 스킵. messageId={}", message.getId());
            ack(message);
            return;
        }

        try {
            REFFillEventPayload payload = objectMapper.readValue(payloadJson, REFFillEventPayload.class);

            if (payload.recognitionId() == null) {
                log.warn("[FillEventConsumer] recognitionId 누락 — ACK 후 스킵. messageId={}, payload={}",
                        message.getId(), payloadJson);
                ack(message);
                return;
            }

            handlePayload(payload);
            processedCounter.increment();
            ack(message);

        } catch (IllegalStateException dup) {
            // 두 번째 처리 시 PENDING이 아니라 발생 — 정상적인 멱등 흐름
            duplicateCounter.increment();
            log.info("[FillEventConsumer] 중복 메시지 (이미 처리됨). messageId={}, msg={}",
                    message.getId(), dup.getMessage());
            ack(message);

        } catch (Exception e) {
            failedCounter.increment();

            // 재시도 횟수 확인 후 dead-letter 처리
            long deliveryCount = getDeliveryCount(message);
            if (deliveryCount >= MAX_DELIVERY_COUNT) {
                moveToDeadLetter(message, payloadJson, e);
                ack(message);
                deadLetterCounter.increment();
            } else {
                // ACK 하지 않음 — XPENDING에 남고 재시도
                log.error("[FillEventConsumer] 처리 실패, 재시도 대기. messageId={}, deliveryCount={}, 사유: {}",
                        message.getId(), deliveryCount, e.getMessage(), e);
            }
        }
    }

    // ── 분기 처리 ─────────────────────────────────────────────────

    private void handlePayload(REFFillEventPayload payload) {
        if (payload.hasEdits()) {
            feedbackLifeCycleService.correctFeedback(commandMapper.toCorrectCommand(payload));
            log.info("[FillEventConsumer] correctFeedback 처리. recognitionId={}, edits={}",
                    payload.recognitionId(), payload.userEditedFields());
        } else {
            feedbackLifeCycleService.approveFeedback(commandMapper.toApproveCommand(payload));
            log.info("[FillEventConsumer] approveFeedback 처리. recognitionId={}", payload.recognitionId());
        }
    }

    // ── ACK ──────────────────────────────────────────────────────

    private void ack(MapRecord<String, String, String> message) {
        try {
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, message.getId());
        } catch (Exception e) {
            // ACK 실패는 운영 알람 대상이지만 처리 자체는 이미 완료됨 (멱등이라 다음 재시도에서 ACK됨)
            log.error("[FillEventConsumer] ACK 실패. messageId={}, 사유: {}",
                    message.getId(), e.getMessage());
        }
    }

    // ── Dead-letter ──────────────────────────────────────────────

    /**
     * XPENDING으로 messageId의 deliveryCount 조회.
     * <p>
     * {@code redisTemplate.opsForStream().pending(streamKey, group)}은 그룹 전체 요약을 주므로,
     * 특정 메시지의 delivery count는 별도 호출이 필요하다.
     * Spring Data Redis는 메시지 단위 XPENDING을 직접 노출하지 않으므로
     * {@code Range}를 좁혀서 단일 메시지만 조회한다.
     * </p>
     * 조회 실패 시 0 반환 (재시도 계속 — 안전한 fallback).
     */
    private long getDeliveryCount(MapRecord<String, String, String> message) {
        try {
            var pendingMessages = redisTemplate.opsForStream().pending(
                    STREAM_KEY,
                    GROUP_NAME,
                    org.springframework.data.domain.Range.just(message.getId().getValue()),
                    1L
            );

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return 0L;
            }

            // 단일 메시지에 대한 XPENDING이므로 첫 요소가 답
            return pendingMessages.iterator().next().getTotalDeliveryCount();

        } catch (Exception e) {
            log.warn("[FillEventConsumer] deliveryCount 조회 실패 — 0으로 가정. messageId={}, error={}",
                    message.getId(), e.getMessage());
            return 0L;
        }
    }

    /**
     * 처리 불가능한 메시지를 dead-letter 스트림으로 이동.
     * 원본은 ACK되어 PEL에서 제거된다.
     */
    private void moveToDeadLetter(
            MapRecord<String, String, String> message, String payloadJson, Exception cause
    ) {
        try {
            StringRecord deadLetterRecord = StreamRecords.string(Map.of(
                            "payload", payloadJson,
                            "originalMessageId", message.getId().getValue(),
                            "errorMessage", cause.getMessage() != null
                                    ? cause.getMessage().substring(0, Math.min(500, cause.getMessage().length()))
                                    : "unknown",
                            "errorClass", cause.getClass().getName()))
                    .withStreamKey(DEAD_LETTER_STREAM);

            RecordId deadLetterId = redisTemplate.opsForStream().add(deadLetterRecord);

            log.error("[FillEventConsumer] DEAD LETTER 이동. originalMessageId={}, deadLetterId={}, 사유: {}",
                    message.getId(), deadLetterId, cause.getMessage(), cause);

        } catch (Exception e) {
            // dead-letter 이동도 실패 — 로그만 남기고 ACK는 그대로 진행
            log.error("[FillEventConsumer] DEAD LETTER 이동 실패. messageId={}, dead-letter 실패: {}, 원본 실패: {}",
                    message.getId(), e.getMessage(), cause.getMessage());
        }
    }
}