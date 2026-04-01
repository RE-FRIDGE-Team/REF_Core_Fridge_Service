package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.recognition_feedback.application.REFFeedbackQueryService;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.domain.event.REFPositiveFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.event.REFProductFeedbackAggregationEvent;
import com.refridge.core_server.recognition_feedback.domain.service.REFProductRegistrationPolicy;
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
 *   Redis REGISTERED 플래그 확인 → 있으면 즉시 리턴 (이미 등록됨)
 *     ↓
 *   Redis 카운터 increment → 현재 누적값 반환
 *     ↓
 *   카운터 < COUNTER_QUICK_FILTER → 리턴 (DB 조회 생략)
 *     ↓
 *   DB 집계 조회 → REFProductRegistrationPolicy.isMet() 검증
 *     ↓
 *   조건 충족 → REFProductFeedbackAggregationEvent 발행
 *     ↓
 *   Product BC 핸들러가 등록 + Redis REGISTERED 플래그 세팅
 * </pre>
 *
 * <h3>Redis 키 구조</h3>
 * <pre>
 *   feedback:positive:{productName}    → 긍정 카운터 (Long)
 *   feedback:registered:{productName}  → 등록 완료 플래그 ("1")
 * </pre>
 *
 * <h3>COUNTER_QUICK_FILTER 주의사항</h3>
 * {@link REFProductRegistrationPolicy#MIN_POSITIVE}와 반드시 동일한 값이어야 합니다.
 * 두 값이 어긋나면 Redis 빠른 차단이 의미를 잃습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFPositiveFeedbackAggregationHandler {

    private final REFFeedbackQueryService feedbackQueryService;
    private final REFProductRegistrationPolicy registrationPolicy;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    // ── Redis 키 프리픽스 ──────────────────────────────────────
    static final String POSITIVE_COUNTER_PREFIX = "feedback:positive:";
    static final String REGISTERED_FLAG_PREFIX  = "feedback:registered:";

    // ── 카운터 빠른 차단 임계값 ────────────────────────────────
    // REFProductRegistrationPolicy.MIN_POSITIVE 와 반드시 동일해야 합니다.
    private static final int COUNTER_QUICK_FILTER = REFProductRegistrationPolicy.MIN_POSITIVE;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(REFPositiveFeedbackEvent event) {
        REFOriginalRecognitionSnapshot snapshot = event.snapshot();
        String productName = snapshot.getProductName();

        if (productName == null || productName.isBlank()) return;

        // 비식재료 반려건의 자동 승인은 Product 등록 대상 아님
        if (snapshot.isRejected()) return;

        // ── Step 1: REGISTERED 플래그 확인 ────────────────────
        if (isAlreadyRegistered(productName)) {
            log.debug("[긍정 피드백 집계] 이미 등록된 제품, 스킵. productName='{}'", productName);
            return;
        }

        // ── Step 2: Redis 카운터 increment ────────────────────
        String counterKey = POSITIVE_COUNTER_PREFIX + productName;
        Long currentCount = redisTemplate.opsForValue().increment(counterKey);

        if (currentCount == null) {
            log.warn("[긍정 피드백 집계] Redis increment 실패, DB 폴백. productName='{}'", productName);
            checkAndPublishEvent(snapshot, productName);
            return;
        }

        log.debug("[긍정 피드백 집계] Redis 카운터 증가. productName='{}', count={}",
                productName, currentCount);

        // ── Step 3: 카운터 빠른 차단 ──────────────────────────
        if (currentCount < COUNTER_QUICK_FILTER) {
            log.debug("[긍정 피드백 집계] 카운터 미달, DB 조회 생략. productName='{}', count={}/{}",
                    productName, currentCount, COUNTER_QUICK_FILTER);
            return;
        }

        // ── Step 4: DB 집계 조회 + 정책 검증 ──────────────────
        checkAndPublishEvent(snapshot, productName);
    }

    private void checkAndPublishEvent(REFOriginalRecognitionSnapshot snapshot,
                                      String productName) {
        REFFeedbackAggregationResult aggregation = feedbackQueryService.getAggregation(productName);

        log.debug("[긍정 피드백 집계] 정책 검증. productName='{}', 긍정={}, 부정={}, score={}",
                productName,
                aggregation.approvedCount(),
                aggregation.correctedCount(),
                registrationPolicy.calculateScore(aggregation));

        if (!registrationPolicy.isMet(aggregation)) {
            return;
        }

        eventPublisher.publishEvent(new REFProductFeedbackAggregationEvent(
                snapshot.getProductName(),
                snapshot.getBrandName(),
                snapshot.getGroceryItemId(),
                snapshot.getImageUrl()
        ));

        log.info("[긍정 피드백 집계] Product 등록 이벤트 발행. productName='{}', groceryItemId={}, score={}",
                productName,
                snapshot.getGroceryItemId(),
                registrationPolicy.calculateScore(aggregation));
    }

    private boolean isAlreadyRegistered(String productName) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(REGISTERED_FLAG_PREFIX + productName));
    }
}