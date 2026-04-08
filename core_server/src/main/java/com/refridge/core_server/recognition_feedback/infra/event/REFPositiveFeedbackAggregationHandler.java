package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.recognition_feedback.application.REFFeedbackQueryService;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.domain.event.REFPositiveFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.event.REFProductFeedbackAggregationEvent;
import com.refridge.core_server.recognition_feedback.domain.service.REFExclusionRemovalPolicy;
import com.refridge.core_server.recognition_feedback.domain.service.REFProductRegistrationPolicy;
import com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot;
import com.refridge.core_server.recognition_feedback.infra.event.improvement.REFExclusionRemovalRedisCounter;
import com.refridge.core_server.recognition_feedback.infra.event.improvement.REFGroceryItemCorrectionService;
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
 * 긍정 피드백을 가장 먼저 마주하는 클래스이며, 부정 피드백을 가장 먼저 마주하는 클래스는
 * {@link REFNegativeFeedbackDispatcher}입니다.
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
 * <li><b>대상:</b> alias 후보 Hash ({@code feedback:product-alias:{name}})
 *     및 식재료명 교정 후보 Hash ({@code feedback:grocery-item-correction:{name}})</li>
 * <li><b>갱신:</b> 긍정 피드백 발생 시 두 Hash 모두 TTL을 {@code CANDIDATE_TTL}(30일)로 연장합니다.</li>
 * </ul>
 *
 * <h4>4. 비식재료 반려 묵인 신호 집계</h4>
 * <p>
 * {@code snapshot.isRejected() == true}인 긍정 피드백은
 * "비식재료로 반려됐지만 사용자가 이의 없이 넘어간" 케이스입니다.
 * 이 신호를 {@link REFExclusionRemovalRedisCounter#incrementAccept}로 집계하여
 * {@link REFExclusionRemovalPolicy}의 Gate 2 (dispute 비율 계산)에 활용합니다.
 * 비식재료 반려 묵인이 많을수록 실제로 비식재료일 가능성이 높아 자동 제거가 억제됩니다.
 * </p>
 *
 * <h4>5. Redis 키 목록</h4>
 * <ul>
 * <li><b>{@code feedback:positive:}</b> Product 등록 전용 카운터</li>
 * <li><b>{@code feedback:registered:}</b> Product 등록 완료 플래그</li>
 * <li><b>{@code feedback:product-alias:}</b> 제품명 alias 후보 Hash ({@code __total__} 포함)</li>
 * <li><b>{@code feedback:grocery-item-correction:}</b> 식재료명 교정 후보 Hash ({@code __total__} 포함)</li>
 * <li><b>{@code feedback:exclusion-accept:}</b> 비식재료 반려 묵인 횟수</li>
 * </ul>
 *
 * @author 이승훈
 * @see REFProductRegistrationPolicy
 * @see REFProductFeedbackAggregationEvent
 * @see REFExclusionRemovalRedisCounter
 * @see REFGroceryItemCorrectionService
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
    private final REFExclusionRemovalRedisCounter exclusionRemovalRedisCounter;

    static final String POSITIVE_COUNTER_PREFIX = "feedback:positive:";
    static final String REGISTERED_FLAG_PREFIX = "feedback:registered:";
    private static final String ALIAS_CANDIDATE_PREFIX = "feedback:product-alias:";
    private static final String ALIAS_TOTAL_FIELD = "__total__";

    /** alias/교정 후보 Hash TTL — 30일간 활동 없으면 자동 만료 */
    private static final Duration CANDIDATE_TTL = Duration.ofDays(30);
    private static final int COUNTER_QUICK_FILTER = REFProductRegistrationPolicy.MIN_POSITIVE;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(REFPositiveFeedbackEvent event) {
        REFOriginalRecognitionSnapshot snapshot = event.snapshot();
        String productName = snapshot.getProductName();

        // ── 비식재료 반려 묵인 신호 집계 ─────────────────────────────
        // rejected=true인 긍정 피드백 = "비식재료 반려됐지만 이의 없이 넘어감"
        // REFExclusionRemovalPolicy Gate 2의 분모(accept)로 활용됨
        if (snapshot.isRejected()) {
            String rejectionKeyword = snapshot.getRejectionKeyword();
            if (rejectionKeyword != null && !rejectionKeyword.isBlank()) {
                exclusionRemovalRedisCounter.incrementAccept(rejectionKeyword);
                log.debug("[긍정 피드백] 비식재료 반려 묵인 집계. keyword='{}', productName='{}'",
                        rejectionKeyword, productName);
            }
            return;
        }

        if (productName == null || productName.isBlank()) return;

        // Step 1: 이미 등록된 제품 → alias/교정 __total__ 갱신 후 early return
        if (isAlreadyRegistered(productName)) {
            log.debug("[긍정 피드백] 이미 등록된 제품, 스킵. productName='{}'", productName);
            incrementAliasTotalWithTtl(productName);
            incrementCorrectionTotalWithTtl(snapshot.getGroceryItemName());
            return;
        }

        // Step 2: alias __total__ +1 + TTL 갱신
        incrementAliasTotalWithTtl(productName);

        // Step 3: 식재료명 교정 __total__ +1 + TTL 갱신
        // 긍정 피드백도 "이 식재료 인식이 맞다"는 반응이므로
        // Gate 2 분모(__total__)에 포함되어 소수 악용 수정본의 확정을 방어
        incrementCorrectionTotalWithTtl(snapshot.getGroceryItemName());

        // Step 4: Product 등록 카운터 increment
        String counterKey = POSITIVE_COUNTER_PREFIX + productName;
        Long currentCount = redisTemplate.opsForValue().increment(counterKey);

        if (currentCount == null) {
            log.warn("[긍정 피드백] Redis increment 실패, DB 폴백. productName='{}'", productName);
            checkAndPublishEvent(snapshot, productName);
            return;
        }

        log.debug("[긍정 피드백] 카운터 증가. productName='{}', count={}", productName, currentCount);

        if (currentCount < COUNTER_QUICK_FILTER) return;

        // Step 5: DB 집계 + 정책 검증
        checkAndPublishEvent(snapshot, productName);
    }

    /**
     * DB에서 피드백 집계를 조회하여 등록 조건 충족 여부를 판단하고,
     * 충족 시 REFProductFeedbackAggregationEvent를 발행합니다.
     */
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
     * 제품명 alias 후보 Hash의 {@code __total__} 값을 1 증가시키고 TTL을 갱신합니다.
     */
    private void incrementAliasTotalWithTtl(String productName) {
        if (productName == null || productName.isBlank()) return;
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
     * 식재료명 교정 후보 Hash의 {@code __total__} 값을 1 증가시키고 TTL을 갱신합니다.
     *
     * <p>
     * {@link REFGroceryItemMappingHandler}가 관리하는 Hash의 분모를 올바르게 유지합니다.
     * groceryItemName이 null이거나 공백이면 조용히 스킵합니다.
     * </p>
     */
    private void incrementCorrectionTotalWithTtl(String groceryItemName) {
        if (groceryItemName == null || groceryItemName.isBlank()) return;
        try {
            String hashKey = REFGroceryItemCorrectionService.CORRECTION_CANDIDATE_PREFIX
                    + groceryItemName;
            redisTemplate.opsForHash().increment(
                    hashKey, REFGroceryItemCorrectionService.TOTAL_FIELD, 1);
            redisTemplate.expire(hashKey, CANDIDATE_TTL);
        } catch (Exception e) {
            log.warn("[긍정 피드백] 식재료명 교정 __total__ 증가 실패. groceryItemName='{}', 사유: {}",
                    groceryItemName, e.getMessage());
        }
    }

    /**
     * 제품명이 이미 등록된 경우를 판단합니다.
     * Redis REGISTERED 플래그 존재 여부로 O(1) 확인합니다.
     */
    private boolean isAlreadyRegistered(String productName) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(REGISTERED_FLAG_PREFIX + productName));
    }
}
