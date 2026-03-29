package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.recognition_feedback.application.REFFeedbackQueryService;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.domain.event.REFPositiveFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 긍정 피드백 이벤트를 구독하여 Product 자동 등록 조건을 판단합니다.
 * <p>
 * 처리 흐름:
 * <ol>
 *   <li>긍정 피드백 카운터 증가 (Redis)</li>
 *   <li>DB 집계 조회 — 긍정 N회 이상 + 부정 비율 10% 미만</li>
 *   <li>조건 충족 시 Product 자동 등록 이벤트 발행</li>
 * </ol>
 * <p>
 * Product 자동 등록 자체는 Product BC의 책임이므로,
 * 여기서는 조건 판단 후 이벤트만 발행하고 실제 등록은 Product BC의 이벤트 핸들러가 처리합니다.
 * (현 단계에서는 로그로 표시하고, Product BC 연동 시 이벤트 발행으로 교체)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFPositiveFeedbackAggregationHandler {

    private final REFFeedbackQueryService feedbackQueryService;
    private final StringRedisTemplate redisTemplate;

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

        // Redis 카운터 증가
        redisTemplate.opsForValue().increment(POSITIVE_COUNTER_PREFIX + productName);

        // DB 집계 조회
        REFFeedbackAggregationResult aggregation = feedbackQueryService.getAggregation(productName);

        if (aggregation.meetsAutoRegistrationThreshold(MIN_POSITIVE_COUNT, MAX_NEGATIVE_RATIO)) {
            triggerProductRegistration(snapshot);
        }
    }

    /**
     * Product 자동 등록을 트리거합니다.
     * 현 단계에서는 로그 출력만 수행합니다.
     * Product BC 연동 시 이벤트 발행으로 교체 예정.
     */
    private void triggerProductRegistration(REFOriginalRecognitionSnapshot snapshot) {
        log.info("[Product 자동등록 조건 충족] productName='{}', groceryItemId={}, groceryItemName='{}', category='{}'",
                snapshot.getProductName(),
                snapshot.getGroceryItemId(),
                snapshot.getGroceryItemName(),
                snapshot.getCategoryPath()
        );

        // TODO: Product BC 연동 시 아래 이벤트 발행으로 교체
        // eventPublisher.publishEvent(new REFProductAutoRegistrationEvent(
        //     snapshot.getProductName(),
        //     snapshot.getGroceryItemId(),
        //     snapshot.getCategoryPath()
        // ));
    }
}