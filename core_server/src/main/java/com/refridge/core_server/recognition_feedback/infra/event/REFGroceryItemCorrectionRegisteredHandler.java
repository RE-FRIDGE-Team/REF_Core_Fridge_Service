package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.product.domain.event.REFProductRegisteredByGroceryItemCorrectionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Product BC가 발행한 식재료명 교정 기반 Product 등록 완료 이벤트를
 * Feedback BC에서 구독하는 핸들러입니다.
 *
 * <h3>BC 경계 준수</h3>
 * <p>
 * Product BC는 Feedback BC의 Redis 키 구조를 알지 못합니다.
 * Product BC는 "Product가 등록됐다"는 사실만 이벤트로 알리고,
 * {@code feedback:registered} 플래그 세팅은 Feedback BC가 자신의 인프라에서 처리합니다.
 * </p>
 *
 * <h3>feedback:registered 플래그의 역할</h3>
 * <p>
 * {@link REFPositiveFeedbackAggregationHandler}는 긍정 피드백 수신 시
 * {@code feedback:registered:{productName}} 키가 존재하면 early return합니다.
 * 이 플래그가 세팅되지 않으면 매 긍정 피드백마다 불필요한 DB 집계 쿼리가 발생합니다.
 * </p>
 *
 * <h3>비동기 실행</h3>
 * <p>
 * {@code @Async}로 실행되어 Product BC의 이벤트 처리 흐름을 블로킹하지 않습니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 * @see REFProductRegisteredByGroceryItemCorrectionEvent
 * @see REFPositiveFeedbackAggregationHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemCorrectionRegisteredHandler {

    private final StringRedisTemplate redisTemplate;

    @Async
    @EventListener
    public void handle(REFProductRegisteredByGroceryItemCorrectionEvent event) {
        log.info("[교정 등록 완료 핸들러] 이벤트 수신. originalName='{}', correctedName='{}'",
                event.originalName(), event.correctedName());

        // Feedback BC가 자신의 Redis 키 구조를 직접 관리
        // REFPositiveFeedbackAggregationHandler의 REGISTERED_FLAG_PREFIX와 동일한 키 구조
        String flagKey = REFPositiveFeedbackAggregationHandler.REGISTERED_FLAG_PREFIX
                + event.originalName();

        redisTemplate.opsForValue().set(flagKey, "1");

        log.info("[교정 등록 완료 핸들러] feedback:registered 플래그 세팅 완료. key='{}'", flagKey);
    }
}