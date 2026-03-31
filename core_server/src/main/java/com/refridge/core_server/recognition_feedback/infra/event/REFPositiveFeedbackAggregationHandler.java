package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.recognition_feedback.application.REFFeedbackQueryService;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.domain.event.REFPositiveFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.event.REFProductAutoRegistrationEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 긍정 피드백 이벤트를 구독하여 Product 자동 등록 조건을 판단합니다.
 *
 * <h3>처리 흐름</h3>
 * <pre>
 *   긍정 피드백 이벤트 수신
 *     ↓
 *   Redis 카운터 increment → 현재 누적값 반환
 *     ↓
 *   누적값 < MIN_POSITIVE_COUNT → 즉시 리턴 (DB 조회 생략)
 *     ↓
 *   누적값 >= MIN_POSITIVE_COUNT → DB 집계 조회 (부정 비율 검증)
 *     ↓
 *   조건 충족 → Product 자동 등록 트리거
 * </pre>
 *
 * <h3>Redis 카운터의 역할</h3>
 * 100만 사용자 규모에서 긍정 피드백 이벤트는 매우 빈번하게 발생합니다.
 * Redis 카운터로 "임계값 미달 여부"를 O(1)으로 판단하여
 * 대부분의 이벤트에서 DB 집계 쿼리(GROUP BY + COUNT)를 완전히 차단합니다.
 *
 * <p>예시: MIN_POSITIVE_COUNT = 5인 경우, 1~4번째 이벤트는 Redis에서 즉시 리턴.
 * 5번째부터만 DB 조회가 발생하므로 DB 부하가 대폭 감소합니다.</p>
 *
 * <h3>Redis 재시작 시 동작</h3>
 * Redis가 재시작되면 카운터가 초기화됩니다. 이 경우 임계값에 이미 도달한 제품도
 * 카운터를 다시 쌓아야 하지만, 결과적으로 DB 조회가 다시 수행되므로 정합성은 유지됩니다.
 * (Product 자동 등록은 멱등하게 처리되어야 합니다.)
 *
 * <h3>주의: TTL 미설정</h3>
 * 카운터에 TTL을 설정하지 않습니다. TTL을 설정하면 카운터가 만료 후 초기화되어
 * 임계값에 도달했던 제품도 재등록 트리거가 다시 발생할 수 있습니다.
 * Redis AOF/RDB 영속성 설정으로 내구성을 보장하는 것을 권장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFPositiveFeedbackAggregationHandler {

    private final REFFeedbackQueryService feedbackQueryService;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** Product 자동 등록을 위한 최소 긍정 피드백 수 */
    private static final int MIN_POSITIVE_COUNT = 5;

    /** Product 자동 등록을 위한 최대 부정 비율 */
    private static final double MAX_NEGATIVE_RATIO = 0.1;

    private static final String POSITIVE_COUNTER_PREFIX = "feedback:positive:";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(REFPositiveFeedbackEvent event) {
        REFOriginalRecognitionSnapshot snapshot = event.snapshot();
        String productName = snapshot.getProductName();

        if (productName == null || productName.isBlank()) return;

        // 비식재료 반려건의 자동 승인은 Product 등록 대상 아님
        if (snapshot.isRejected()) return;

        // Redis 카운터 increment → 현재 누적값 반환
        // increment()는 원자적으로 동작하므로 동시 요청에도 안전
        String counterKey = POSITIVE_COUNTER_PREFIX + productName;
        Long currentCount = redisTemplate.opsForValue().increment(counterKey);

        if (currentCount == null) {
            // Redis 장애 상황 — 카운터 없이 DB 직접 조회로 폴백
            log.warn("[긍정 피드백 집계] Redis increment 실패, DB 폴백. productName='{}'", productName);
            checkAndTriggerFromDb(snapshot, productName);
            return;
        }

        log.debug("[긍정 피드백 집계] Redis 카운터 증가. productName='{}', count={}", productName, currentCount);

        // 임계값 미달이면 DB 조회 자체를 생략
        if (currentCount < MIN_POSITIVE_COUNT) {
            log.debug("[긍정 피드백 집계] 임계값 미달, DB 조회 생략. productName='{}', count={}/{}",
                    productName, currentCount, MIN_POSITIVE_COUNT);
            return;
        }

        // 임계값 도달 이후 — DB 집계 조회로 부정 비율까지 검증
        checkAndTriggerFromDb(snapshot, productName);
    }

    /**
     * DB 집계 조회 후 자동 등록 조건을 검사합니다.
     * Redis 폴백 경로와 임계값 도달 경로 모두에서 사용됩니다.
     */
    private void checkAndTriggerFromDb(REFOriginalRecognitionSnapshot snapshot, String productName) {
        REFFeedbackAggregationResult aggregation = feedbackQueryService.getAggregation(productName);

        if (aggregation.meetsAutoRegistrationThreshold(MIN_POSITIVE_COUNT, MAX_NEGATIVE_RATIO)) {
            triggerProductRegistration(snapshot);
        }
    }

    /**
     * Product BC로 자동 등록 이벤트를 발행합니다.
     * <p>
     * majorCategoryId/minorCategoryId는 이벤트에 포함하지 않습니다.
     * Product BC 핸들러가 groceryItemId로 REFGroceryItemRepository를 조회하여 직접 채웁니다.
     * 피드백 BC가 GroceryItem 도메인을 알 필요가 없도록 책임을 분리한 설계입니다.
     */
    private void triggerProductRegistration(REFOriginalRecognitionSnapshot snapshot) {
        REFProductAutoRegistrationEvent autoRegistrationEvent = new REFProductAutoRegistrationEvent(
                snapshot.getProductName(),
                snapshot.getBrandName(),
                snapshot.getGroceryItemId(),
                snapshot.getImageUrl()
        );

        eventPublisher.publishEvent(autoRegistrationEvent);

        log.info("[Product 자동등록] 이벤트 발행. productName='{}', groceryItemId={}, brandName='{}'",
                snapshot.getProductName(),
                snapshot.getGroceryItemId(),
                snapshot.getBrandName()
        );
    }
}