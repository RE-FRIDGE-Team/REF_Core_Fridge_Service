package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.recognition_feedback.application.REFFeedbackQueryService;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.domain.event.REFPositiveFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.event.REFProductFeedbackAggregationEvent;
import com.refridge.core_server.recognition_feedback.domain.service.REFProductRegistrationPolicy;
import com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot;
import com.refridge.core_server.recognition_feedback.infra.event.improvement.REFNegativeFeedbackDispatcher;
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
 * 발생하는 긍정 피드백 이벤트를 처리합니다.<br>
 * 긍정 피드백을 가장 먼저 마주하는 클래스이며, 부정 피드백을 가정 먼저 마주하는 클래스는 {@link REFNegativeFeedbackDispatcher}입니다.
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
 * <h4>4. Redis 키와 동작에 대한 설명</h4>
 * <ul>
 * <li><b>{@code feedback:positive:}</b> product 등록만을 위한 카운터이며, 임계값을 넘으면 등록 이벤트를 발행. 초기화 되더라도 등록 이벤트는 upsert이기 때문에 상관 없음.</li>
 * <li><b>{@code feedback:registered:}</b> 긍정 피드백을 통해 제품이 등록된 경우를 나타내는 태그, 초기화 되더라도 등록 이벤트는 upsert이기 때문에 상관 없음.</li>
 * <li><b>{@code feedback:product-alias:}</b> {@code __total__}과 부정 피드백에서 사용자의 수정본을 값으로 들고 있는 해시. 긍정값은 별도로 없고 {@code __total__}에서 나머지 카운터 값을 뺀 것으로 관리</li>
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

    /**
     * Redis Key Prefixes, 자세한 내용은 위의 Javadocs 참고.
     */
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
        // feedback:positive: 카운터는 등록 판단 전용이므로 REGISTERED 이후 증가 불필요.
        if (isAlreadyRegistered(productName)) {
            log.debug("[긍정 피드백] 이미 등록된 제품, 스킵. productName='{}'", productName);
            incrementAliasTotalWithTtl(productName);
            return;
        }

        // Step 2: alias __total__ +1 + TTL 갱신
        incrementAliasTotalWithTtl(productName);

        // Step 3: Product 등록 카운터 increment
        // Product 자동 등록 조건 판단 전용 카운터. 사용자가 인식 결과를 수정 없이 승인할 때마다 +1.
        // 이 값이 일정 임계값에 도달하면 DB를 조회해 등록 정책을 검증하고 등록 이벤트를 발행한다.
        String counterKey = POSITIVE_COUNTER_PREFIX + productName;
        Long currentCount = redisTemplate.opsForValue().increment(counterKey);

        // increment 실패 시 DB 조회로 폴백.
        // @TransactionalEventListener(AFTER_COMMIT) 기준이므로
        // DB의 approvedCount에 현재 피드백이 이미 포함되어 있음.
        // DB count가 Redis increment보다 정확하므로 별도 increment 없이 DB 조회만으로 충분함.
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

    /**
     * DB에서 피드백 집계를 조회하여 등록 조건 충족 여부를 판단하고,<br>
     * 충족 시 REFProductFeedbackAggregationEvent를 발행합니다.
     *
     * @param snapshot    인식 파이프라인이 만들어 낸 결과의 원본 스냅샷
     * @param productName 제품명 (원본 또는 alias) - DB 집계 조회의 기준이 됩니다.
     */
    private void checkAndPublishEvent(REFOriginalRecognitionSnapshot snapshot,
                                      String productName) {
        REFFeedbackAggregationResult aggregation =
                feedbackQueryService.getAggregation(productName);

        // 내부 정책 조건 충족 확인
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
     * 긍정 피드백을 받았으므로, {@code __total__} 값만 증가시킨다.<br>
     * TTL 30일 추가 : 긍정 피드백도 해당 제품에 대한 활동이므로 만료 시점을 연장합니다.
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

    /**
     * 제품명이 이미 등록된 경우를 판단합니다. Redis에서 REGISTERED 플래그가 세팅된 경우 등록된 것으로 간주합니다.
     *
     * @param productName
     * @return {@code true} 등록된 제품, {@code false} 미등록 제품
     */
    private boolean isAlreadyRegistered(String productName) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(REGISTERED_FLAG_PREFIX + productName));
    }
}