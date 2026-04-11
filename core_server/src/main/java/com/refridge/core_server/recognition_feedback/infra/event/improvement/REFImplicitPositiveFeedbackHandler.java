package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionDiff;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Set;

/**
 * 부정 피드백에서 암묵적 긍정 신호를 추출하여 각 Hash의 {@code __total__}을 증가시키는 핸들러입니다.
 *
 * <h3>역할</h3>
 * <p>
 * 부정 피드백에서 사용자가 수정하지 않은 필드는 "파이프라인이 이 부분은 맞췄다"는 암묵적 동의입니다.
 * 해당 필드를 관리하는 각 핸들러의 {@code __total__} 분모를 올려서,
 * 소수 outlier 수정본이 확정/검수 큐에 적재되는 것을 방어합니다.
 * </p>
 *
 * <h3>__total__ 관리 책임 분담</h3>
 * <p>
 * 모든 피드백(긍정이든 부정이든)이 모든 필드의 {@code __total__}에 반영되어야 합니다.
 * 세 가지 경로로 분담합니다:
 * </p>
 * <ol>
 *   <li><b>순수 긍정 피드백</b>
 *       → {@code REFPositiveFeedbackAggregationHandler} (4개 Hash 모두 +1)</li>
 *   <li><b>부정 피드백에서 해당 필드 수정</b>
 *       → 각 개선 핸들러 (수정된 필드의 Hash만 +1)</li>
 *   <li><b>부정 피드백에서 해당 필드 미수정</b>
 *       → 이 핸들러 (미수정된 필드의 Hash만 +1)</li>
 * </ol>
 *
 * <p>케이스별 동작 예시:</p>
 * <pre>
 *   긍정 피드백          → (1)로 4개 모두 +1
 *   부정 (브랜드만 수정) → (2)로 brand Hash +1, (3)으로 나머지 3개 Hash +1
 *   부정 (전부 수정)     → (2)로 4개 모두 +1, (3)은 아무것도 안 함
 * </pre>
 *
 * <h3>처리 대상 필드와 올리는 Hash</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead>
 *     <tr style="background:#f0f0f0;">
 *       <th>confirmedField</th>
 *       <th>올릴 __total__ Hash 키</th>
 *       <th>의미</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code PRODUCT_NAME}</td>
 *       <td>{@code feedback:product-alias:{productName}}</td>
 *       <td>"제품명 맞다" → alias 수정본 방어</td>
 *     </tr>
 *     <tr>
 *       <td>{@code GROCERY_ITEM}</td>
 *       <td>{@code feedback:grocery-item-correction:{groceryItemName}}</td>
 *       <td>"식재료명 맞다" → correction 수정본 방어</td>
 *     </tr>
 *     <tr>
 *       <td>{@code BRAND}</td>
 *       <td>{@code feedback:brand-correction:{brandName}}</td>
 *       <td>"브랜드 맞다" → 브랜드 교체 방어</td>
 *     </tr>
 *     <tr>
 *       <td>{@code CATEGORY}</td>
 *       <td>{@code feedback:category-reassignment:{categoryPath}}</td>
 *       <td>"카테고리 맞다" → 카테고리 outlier 방어</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>QUANTITY_VOLUME 미처리</h3>
 * <p>
 * {@link REFCorrectionType#QUANTITY_VOLUME}이 {@code confirmedFields}에 포함되어도
 * 현재는 처리하지 않습니다. 대응하는 {@code __total__} Hash가 없기 때문입니다.
 * 향후 파서 품질 메트릭 도입 시 확장 가능합니다.
 * </p>
 *
 * <h3>Dispatcher와의 관계</h3>
 * <p>
 * 이 핸들러는 {@link REFImprovementActionHandler}를 구현하지 않습니다.
 * {@link REFNegativeFeedbackDispatcher}와 독립적으로 동일한 이벤트를 구독하되
 * 관심사가 다릅니다:
 * </p>
 * <ul>
 *   <li><b>Dispatcher</b>: {@code changedFields} → 사전/alias/검수 큐 개선</li>
 *   <li><b>이 핸들러</b>: {@code confirmedFields} → {@code __total__} 분모 증가 (방어적)</li>
 * </ul>
 *
 * <h3>예외 격리</h3>
 * <p>
 * Spring의 {@code @TransactionalEventListener}는 동일 phase의 리스너를 순차 실행합니다.
 * {@code handle()} 전체를 try-catch로 감싸서 이 핸들러의 예외가 Dispatcher 실행에
 * 영향을 주지 않도록 합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFCorrectionDiff#calculateConfirmedFields(com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot, REFCorrectionDiff)
 * @see REFNegativeFeedbackDispatcher
 * @see com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFImplicitPositiveFeedbackHandler {

    private final StringRedisTemplate redisTemplate;

    // ── 키 접두사 ────────────────────────────────────────────────────────────

    private static final String ALIAS_CANDIDATE_PREFIX       = "feedback:product-alias:";
    private static final String CORRECTION_CANDIDATE_PREFIX  = "feedback:grocery-item-correction:";
    private static final String BRAND_CORRECTION_PREFIX      = "feedback:brand-correction:";
    private static final String CATEGORY_REASSIGNMENT_PREFIX = "feedback:category-reassignment:";

    // ── 상수 ─────────────────────────────────────────────────────────────────

    private static final String TOTAL_FIELD    = "__total__";
    private static final Duration CANDIDATE_TTL = Duration.ofDays(30);

    // ── 이벤트 핸들러 ────────────────────────────────────────────────────────

    /**
     * 부정 피드백 이벤트를 수신하여 암묵적 긍정 필드의 {@code __total__}을 증가시킵니다.
     *
     * @param event 부정 피드백 도메인 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(REFNegativeFeedbackEvent event) {
        try {
            // ── Guard 1: 비식재료 반려 케이스 ────────────────────────────────
            // rejected 케이스는 정상 매칭이 아니므로 "필드가 맞다"는 신호가 될 수 없음.
            // REFExclusionRemovalHandler가 별도로 처리.
            if (event.snapshot().isRejected()) return;

            // ── Guard 2: 변경 필드 없음 (방어 코드) ──────────────────────────
            // hasNoChanges()가 true이면 CORRECTED 상태로 저장되지 않으므로
            // 이 이벤트가 발행되지 않아야 정상이나, 방어적으로 처리
            if (event.diff().hasNoChanges()) return;

            // ── confirmedFields 계산 ──────────────────────────────────────────
            // "수정하지 않은 필드 중 원본에 값이 있었던 필드" = 암묵적 동의 필드
            Set<REFCorrectionType> confirmed = REFCorrectionDiff.calculateConfirmedFields(
                    event.snapshot(), event.diff());

            if (confirmed.isEmpty()) return;

            log.info("[암묵적 긍정] confirmedFields={}, productName='{}', groceryItem='{}', " +
                            "brand='{}', category='{}', feedbackId={}",
                    confirmed,
                    event.snapshot().getProductName(),
                    event.snapshot().getGroceryItemName(),
                    event.snapshot().getBrandName(),
                    event.snapshot().getCategoryPath(),
                    event.feedbackId().getValue());

            // ── 각 confirmedField별 __total__ 증가 ───────────────────────────

            if (confirmed.contains(REFCorrectionType.PRODUCT_NAME)) {
                String productName = event.snapshot().getProductName();
                if (productName != null && !productName.isBlank()) {
                    incrementTotal(ALIAS_CANDIDATE_PREFIX + productName);
                }
            }

            if (confirmed.contains(REFCorrectionType.GROCERY_ITEM)) {
                String groceryItemName = event.snapshot().getGroceryItemName();
                if (groceryItemName != null && !groceryItemName.isBlank()) {
                    incrementTotal(CORRECTION_CANDIDATE_PREFIX + groceryItemName);
                }
            }

            if (confirmed.contains(REFCorrectionType.BRAND)) {
                String brandName = event.snapshot().getBrandName();
                if (brandName != null && !brandName.isBlank()) {
                    incrementTotal(BRAND_CORRECTION_PREFIX + brandName);
                }
            }

            if (confirmed.contains(REFCorrectionType.CATEGORY)) {
                String categoryPath = event.snapshot().getCategoryPath();
                if (categoryPath != null && !categoryPath.isBlank()) {
                    incrementTotal(CATEGORY_REASSIGNMENT_PREFIX + categoryPath);
                }
            }

            // QUANTITY_VOLUME: 대응하는 __total__ Hash가 없으므로 현재 미처리.
            // 향후 파서 품질 메트릭 도입 시 확장 예정.

        } catch (Exception e) {
            // Dispatcher와 순차 실행되므로 이 핸들러의 예외가 Dispatcher에 영향을 주지 않도록 격리
            log.error("[암묵적 긍정] 처리 중 예외 발생. feedbackId={}, 사유: {}",
                    event.feedbackId().getValue(), e.getMessage());
        }
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────

    /**
     * 지정된 Hash 키의 {@code __total__} 필드를 1 증가시키고 TTL을 갱신합니다.
     * <p>
     * Redis 장애 시 warn 로그만 남기고 조용히 스킵합니다.
     * 하나의 Hash 실패가 다른 Hash 증가를 막지 않도록 개별 try-catch로 감쌉니다.
     *
     * @param hashKey {@code feedback:*:{originalValue}} 형태의 Hash 키
     */
    private void incrementTotal(String hashKey) {
        try {
            redisTemplate.opsForHash().increment(hashKey, TOTAL_FIELD, 1);
            redisTemplate.expire(hashKey, CANDIDATE_TTL);
        } catch (Exception e) {
            log.warn("[암묵적 긍정] __total__ 증가 실패. key='{}', 사유: {}",
                    hashKey, e.getMessage());
        }
    }
}