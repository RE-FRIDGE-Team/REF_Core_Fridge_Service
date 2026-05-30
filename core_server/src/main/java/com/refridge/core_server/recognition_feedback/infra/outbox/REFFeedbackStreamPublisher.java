package com.refridge.core_server.recognition_feedback.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Feedback Outbox → Redis Stream XADD 래퍼.
 *
 * <h2>역할</h2>
 * {@link REFFeedbackOutboxRelayer}로부터 이벤트 페이로드를 받아
 * Redis Stream에 {@code XADD}로 발행한다.
 *
 * <h2>메시지 구조</h2>
 * Stream entry는 {@code payload} 필드 하나로 구성:
 * <pre>
 * XADD feedback:grocery-item-correction-confirmed * payload "{...eventJson...}"
 * </pre>
 *
 * @author 승훈
 * @since 2026-05-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFFeedbackStreamPublisher {

    private static final String PAYLOAD_FIELD = "payload";

    private final StringRedisTemplate redis;

    public RecordId publish(String streamKey, String payload) {
        StringRecord record = StreamRecords.string(Map.of(PAYLOAD_FIELD, payload))
                .withStreamKey(streamKey);

        RecordId recordId = redis.opsForStream().add(record);

        log.debug("[REFFeedbackStreamPublisher] XADD 완료. streamKey={}, recordId={}",
                streamKey, recordId);

        return recordId;
    }
}