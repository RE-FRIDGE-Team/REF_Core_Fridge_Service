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

import java.time.Duration;

/**
 * <h3>긍정 피드백 구독 및 제품 등록 검증 핸들러</h3>
 *
 * <p>
 * {@code RE:FRIDGE}의 5단계 인식 파이프라인 결과를 사용자가 수정 없이 수락('냉장고에 추가')할 때
 * 발생하는 긍정 피드백 이벤트를 처리합니다.
 * </p>
 *
 * <blockquote>
 * <b>TODO:</b> 향후 별도의 냉장고 도메인 서버 분리 및 이벤트 스트리밍 Kafka 도입을 검토합니다.
 * </blockquote>
 *
 * <h4>1. 카운터 기반 성능 최적화 (Fast-Filtering)</h4>
 * <ul>
 * <li><b>Redis 활용:</b> {@code feedback:positive:{productName}} 키를 통해 제품별 긍정 피드백 수를 관리합니다.</li>
 * <li><b>Early Return:</b> 카운터가 {@link REFProductRegistrationPolicy#MIN_POSITIVE} 미만인 경우,
 * DB 접근 없이 즉시 종료하여 시스템 부하를 최소화합니다.</li>
 * <li><b>DB 집계:</b> 임계값 도달 시점에만 DB에서 정확한 집계를 수행하여 등록 조건을 최종 검증합니다.</li>
 * </ul>
 *
 * <h4>2. 제품 등록 프로세스</h4>
 * <p>
 * 모든 검증 조건({@link REFProductRegistrationPolicy} 참고)이 충족되면,
 * 정식 제품 등록을 위한 {@code REFProductFeedbackAggregationEvent}를 발행합니다.
 * </p>
 *
 * <h4>3. Redis TTL 관리 정책</h4>
 * <ul>
 * <li><b>대상:</b> 별칭(Alias) 후보 Hash 데이터 ({@code feedback:product-alias:{name}})</li>
 * <li><b>갱신:</b> 긍정 피드백 발생 시 해당 제품을 '활성 상태'로 간주하여 TTL을
 * {@code CANDIDATE_TTL}(30일)로 자동 연장합니다.</li>
 * </ul>
 *
 * @author 이승훈
 * @see REFProductRegistrationPolicy
 * @see REFProductFeedbackAggregationEvent
 * @since 2026. 4. 3.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFPositiveFeedbackAggregationHandler {

    private final REFFeedbackQueryService feedbackQueryService;
    private final REFProductRegistrationPolicy registrationPolicy;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    static final String POSITIVE_COUNTER_PREFIX = "feedback:positive:";
    static final String REGISTERED_FLAG_PREFIX = "feedback:registered:";
    private static final String ALIAS_CANDIDATE_PREFIX = "feedback:product-alias:";

    private static final String ALIAS_TOTAL_FIELD = "__total__";

    /**
     * alias 후보 Hash TTL — 30일간 활동 없으면 자동 만료
     */
    private static final Duration CANDIDATE_TTL = Duration.ofDays(30);

    private static final int COUNTER_QUICK_FILTER = REFProductRegistrationPolicy.MIN_POSITIVE;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(REFPositiveFeedbackEvent event) {
        REFOriginalRecognitionSnapshot snapshot = event.snapshot();
        String productName = snapshot.getProductName();

        // * early return 조건: 제품명 누락/공백, 피드백 거절된 경우
        if (productName == null || productName.isBlank()) return;
        if (snapshot.isRejected()) return;

        // Step 1: 이미 제품으로 등록된 경우 early return + alias __total__ +1 + TTL 갱신
        // TODO : Product 등록 카운터 increment도 해야지
        if (isAlreadyRegistered(productName)) {
            log.debug("[긍정 피드백] 이미 등록된 제품, 스킵. productName='{}'", productName);
            incrementAliasTotalWithTtl(productName);
            return;
        }

        // Step 2: alias __total__ +1 + TTL 갱신
        incrementAliasTotalWithTtl(productName);

        // Step 3: Product 등록 카운터 increment
        String counterKey = POSITIVE_COUNTER_PREFIX + productName;
        Long currentCount = redisTemplate.opsForValue().increment(counterKey);

        // TODO : 다시 깊게 생각. increment 실패 -> db 조회 후 복원하면 increment를 안 해도 되나?
        if (currentCount == null) {
            log.warn("[긍정 피드백] Redis increment 실패, DB 폴백. productName='{}'", productName);
            checkAndPublishEvent(snapshot, productName);
            return;
        }

        log.debug("[긍정 피드백] 카운터 증가. productName='{}', count={}", productName, currentCount);

        if (currentCount < COUNTER_QUICK_FILTER) return;

        // ── Step 4: DB 집계 + 정책 검증 ───────────────────────────
        checkAndPublishEvent(snapshot, productName);
    }

    private void checkAndPublishEvent(REFOriginalRecognitionSnapshot snapshot,
                                      String productName) {
        REFFeedbackAggregationResult aggregation =
                feedbackQueryService.getAggregation(productName);

        if (!registrationPolicy.isMet(aggregation)) return;

        eventPublisher.publishEvent(new REFProductFeedbackAggregationEvent(
                snapshot.getProductName(),
                snapshot.getBrandName(),
                snapshot.getGroceryItemId(),
                snapshot.getImageUrl()
        ));

        log.info("[긍정 피드백] Product 등록 이벤트 발행. productName='{}', score={}",
                productName, registrationPolicy.calculateScore(aggregation));
    }

    /**
     * alias 후보 Hash의 __total__ +1 및 TTL을 30일로 갱신합니다.
     * 긍정 피드백도 해당 제품에 대한 활동이므로 만료 시점을 연장합니다.
     */
    private void incrementAliasTotalWithTtl(String productName) {
        try {
            String hashKey = ALIAS_CANDIDATE_PREFIX + productName;
            redisTemplate.opsForHash().increment(hashKey, ALIAS_TOTAL_FIELD, 1);
            redisTemplate.expire(hashKey, CANDIDATE_TTL);
        } catch (Exception e) {
            log.warn("[긍정 피드백] alias __total__ 증가 실패. productName='{}', 사유: {}",
                    productName, e.getMessage());
        }
    }

    private boolean isAlreadyRegistered(String productName) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(REGISTERED_FLAG_PREFIX + productName));
    }
}