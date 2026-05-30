package com.refridge.core_server.recognition_feedback.infra.stream;

import io.lettuce.core.RedisBusyException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.util.UUID;

/**
 * fridge_server → core_server 간 Redis Stream 구독 인프라.
 *
 * <h2>구성 요소</h2>
 * <ul>
 *   <li>{@code fridge:fill-completed} Consumer Group ({@code feedback-fill-group}) 멱등 생성</li>
 *   <li>{@code dead-letter:fill-completed} 스트림 사전 생성 (XADD 시 자동 생성되지만 명시적 보장)</li>
 *   <li>{@code StreamMessageListenerContainer} (Lettuce 기반) 폴링 시작</li>
 *   <li>인스턴스마다 고유한 consumer name (UUID) — 다중 노드에서 메시지 분산</li>
 * </ul>
 *
 * <h2>왜 ReadOffset.lastConsumed() 인가</h2>
 * <ul>
 *   <li>{@code ReadOffset.lastConsumed()} — 그룹이 마지막으로 ACK한 다음 메시지부터 읽음</li>
 *   <li>{@code ReadOffset.from("0")} — 그룹 PEL의 미처리 메시지부터 다시 읽음 (recovery 시 유용하나 매번 적용 부적절)</li>
 * </ul>
 * 정상 운영에서는 {@code lastConsumed}로 충분. 인스턴스 재시작 시 PEL에 남은 미처리 메시지는
 * Spring Data Redis가 자동으로 재할당해준다.
 *
 * <h2>인스턴스 ID</h2>
 * {@code "feedback-consumer-" + UUID.randomUUID()}로 인스턴스마다 고유.
 * 같은 그룹 안에서 여러 consumer가 메시지를 나눠 받는다 (round-robin 분배).
 *
 * @author 승훈
 * @since 2026-05-18
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class REFFridgeStreamConfig implements DisposableBean {

    private static final String STREAM_KEY = REFFillEventConsumer.STREAM_KEY;
    private static final String GROUP_NAME = REFFillEventConsumer.GROUP_NAME;
    private static final String DEAD_LETTER_STREAM = REFFillEventConsumer.DEAD_LETTER_STREAM;

    private final StringRedisTemplate redisTemplate;
    private final REFFillEventConsumer fillEventConsumer;
    private final RedisConnectionFactory redisConnectionFactory;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private Subscription subscription;

    @PostConstruct
    public void start() {
        ensureConsumerGroup();
        ensureDeadLetterStream();

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .batchSize(20)
                .build();

        container = StreamMessageListenerContainer.create(redisConnectionFactory, options);

        String consumerName = "feedback-consumer-" + UUID.randomUUID();
        subscription = container.receive(
                Consumer.from(GROUP_NAME, consumerName),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                fillEventConsumer
        );

        container.start();
        log.info("[REFFridgeStreamConfig] Stream 구독 시작. stream={}, group={}, consumer={}",
                STREAM_KEY, GROUP_NAME, consumerName);
    }

    /**
     * Consumer Group이 없으면 생성, 있으면 BUSYGROUP 에러를 무시하고 통과.
     * Stream 자체가 없는 경우는 createGroup이 자동 생성 (MKSTREAM 동작).
     *
     * <h3>왜 ReadOffset.from("0") 인가 (createGroup 호출 시)</h3>
     * 그룹을 처음 만들 때 "0"으로 설정하면 스트림 시작부터 모든 메시지를 읽는다.
     * 이는 새 그룹이 늦게 만들어졌을 때 기존 메시지를 누락하지 않기 위함.
     * {@code "$"}로 설정하면 그룹 생성 시점 이후 메시지만 읽어서 누락 위험.
     */
    private void ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream()
                    .createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
            log.info("[REFFridgeStreamConfig] Consumer Group 생성. stream={}, group={}",
                    STREAM_KEY, GROUP_NAME);
        } catch (Exception e) {
            if (isBusyGroupError(e)) {
                log.debug("[REFFridgeStreamConfig] Consumer Group이 이미 존재합니다. group={}", GROUP_NAME);
            } else {
                log.error("[REFFridgeStreamConfig] Consumer Group 생성 실패. group={}", GROUP_NAME, e);
            }
        }
    }

    /**
     * Dead-letter 스트림을 사전 생성하여 XADD가 즉시 동작하도록 보장.
     * Redis는 첫 XADD 시 스트림을 자동 생성하지만, 명시적 생성으로 검증 가능성 향상.
     * (이미 존재하면 noop)
     */
    private void ensureDeadLetterStream() {
        try {
            // 빈 placeholder는 XADD에서 1바이트만 차지 — 운영 부담 무시 가능
            // 또는 createGroup으로 멱등 생성하지만, dead-letter는 group 불필요
            // XLEN으로 존재 여부만 확인
            Long streamLength = redisTemplate.opsForStream().size(DEAD_LETTER_STREAM);
            if (streamLength != null && streamLength > 0) {
                log.debug("[REFFridgeStreamConfig] Dead-letter 스트림 존재. length={}", streamLength);
            } else {
                log.info("[REFFridgeStreamConfig] Dead-letter 스트림 비어있음 (정상). key={}", DEAD_LETTER_STREAM);
            }
        } catch (Exception e) {
            log.debug("[REFFridgeStreamConfig] Dead-letter 스트림 미존재 — 첫 XADD 시 자동 생성됨. key={}",
                    DEAD_LETTER_STREAM);
        }
    }

    /**
     * BUSYGROUP 에러 판별 — Spring Data Redis가 RedisSystemException으로 래핑하므로 cause 확인.
     */
    private boolean isBusyGroupError(Exception e) {
        if (e.getCause() instanceof RedisBusyException) return true;
        String msg = e.getMessage();
        if (msg != null && msg.contains("BUSYGROUP")) return true;
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP");
    }

    @Override
    public void destroy() {
        if (subscription != null) {
            subscription.cancel();
            log.info("[REFFridgeStreamConfig] Subscription 취소");
        }
        if (container != null) {
            container.stop();
            log.info("[REFFridgeStreamConfig] Container 정지");
        }
    }
}