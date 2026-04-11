package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.product_recognition.domain.REFRecognitionDictionaryRepository;
import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.event.REFDictionarySyncedEvent;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import com.refridge.core_server.product_recognition.infra.sync.REFDictionarySyncEventHandler;
import com.refridge.core_server.product_recognition.infra.sync.REFRecognitionDictionaryRedisSync;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.port.REFGroceryItemExistencePort;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import com.refridge.core_server.recognition_feedback.domain.service.REFExclusionRemovalPolicy;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 비식재료로 반려되었으나 사용자가 식재료로 수정한 피드백을 처리합니다.
 *
 * <h3>처리 흐름 — 반자동화 3중 게이트</h3>
 * <pre>
 *   handle(event)
 *     │
 *     ├─ [Step 1] dispute 카운터 +1 (Redis)
 *     │
 *     ├─ [Step 2] Gate 1: disputeCount < MIN_REMOVAL_COUNT(5) → 검수 큐 적재 후 리턴
 *     │
 *     ├─ [Step 3] Gate 3 (이분화)
 *     │     │
 *     │     ├─ correctedGroceryItemName 유효성 검사 실패
 *     │     │     (null/공백/15자 초과/특수문자만)
 *     │     │     → EXCLUSION_REMOVAL 검수 큐만 적재 후 리턴
 *     │     │
 *     │     ├─ GroceryItem DB에 존재 → Gate 2로 진행
 *     │     │
 *     │     └─ 유효하지만 DB에 없음 (미등록 식재료 추정)
 *     │           → EXCLUSION_REMOVAL + NEW_GROCERY_ITEM 동시 적재 후 리턴
 *     │             (자동 제거 안 함 — 관리자가 두 건을 함께 판단)
 *     │
 *     ├─ [Step 4] Gate 2: disputeRatio < 0.6 → 검수 큐 적재 후 리턴
 *     │
 *     └─ [Step 5] 자동 제거 실행
 *                   → REFRecognitionDictionary.removeEntry(rejectionKeyword)   [DB]
 *                   → REFRecognitionDictionaryRedisSync.sync()                 [Redis Set]
 *                   → TransactionSynchronization.afterCommit() 등록            [커밋 후]
 *                       → REFDictionarySyncedEvent(EXCLUSION)                  [Trie 재빌드]
 *                   → Redis 카운터 초기화
 *                   → 감사 로그용 검수 항목 적재 (contextDetail에 [AUTO_REMOVED] 마킹)
 * </pre>
 *
 * <h3>Trie 재빌드 타이밍 — 커밋 후 분리 (이슈 1.2 수정)</h3>
 * <p>
 * 기존에는 {@code REQUIRES_NEW} 트랜잭션 내에서 {@code eventPublisher.publishEvent()}를
 * 즉시 호출하여 동기 {@code @EventListener}인 {@link REFDictionarySyncEventHandler}가
 * 같은 트랜잭션 안에서 Trie를 재빌드했습니다.
 * 이 경우 트랜잭션 롤백 시 DB 키워드 삭제는 원복되지만 Trie는 이미 변경되어
 * DB ↔ Trie 불일치가 발생할 수 있습니다.
 * </p>
 * <p>
 * 수정 후에는 {@link TransactionSynchronizationManager#registerSynchronization}을 통해
 * {@link TransactionSynchronization#afterCommit()} 콜백에서만 이벤트를 발행합니다.
 * 트랜잭션이 성공적으로 커밋된 경우에만 Trie가 재빌드되어 DB와의 일관성이 보장됩니다.
 * </p>
 *
 * <h3>트랜잭션 경계</h3>
 * <p>
 * {@code REFNegativeFeedbackDispatcher}는 {@code @TransactionalEventListener(AFTER_COMMIT)} 이후
 * 트랜잭션 경계 밖에서 호출됩니다. {@code REQUIRES_NEW}로 명시하여 자동 제거 DB 작업이
 * 독립적인 트랜잭션으로 커밋되도록 합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 6.
 * @see REFExclusionRemovalPolicy
 * @see REFExclusionRemovalRedisCounter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFExclusionRemovalHandler implements REFImprovementActionHandler {

    private final REFFeedbackReviewItemRepository reviewItemRepository;
    private final REFExclusionRemovalPolicy exclusionRemovalPolicy;
    private final REFExclusionRemovalRedisCounter redisCounter;
    private final REFGroceryItemExistencePort groceryItemExistencePort;
    private final REFRecognitionDictionaryRepository dictionaryRepository;
    private final REFRecognitionDictionaryRedisSync redisSync;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 유효한 식재료명으로 간주할 최대 글자 수.
     * 이 길이를 초과하면 사용자 실수로 간주하여 NEW_GROCERY_ITEM 검수 큐를 적재하지 않습니다.
     */
    private static final int MAX_GROCERY_ITEM_NAME_LENGTH = 15;

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.REJECTED_BUT_FOOD;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(REFNegativeFeedbackEvent event) {
        String rejectionKeyword = event.snapshot().getRejectionKeyword();
        if (rejectionKeyword == null || rejectionKeyword.isBlank()) {
            log.warn("[비식재료 사전] 반려 키워드 누락. feedbackId={}", event.feedbackId().getValue());
            return;
        }

        String correctedGroceryItemName = event.correction().getCorrectedGroceryItemName();

        // ── Step 1: dispute 카운터 증가 ─────────────────────────────
        Long disputeCount = redisCounter.incrementDispute(rejectionKeyword);
        if (disputeCount == null) {
            log.warn("[비식재료 사전] Redis 카운터 증가 실패, 검수 큐로 폴백. keyword='{}'",
                    rejectionKeyword);
            enqueueExclusionReview(event, rejectionKeyword, correctedGroceryItemName, false);
            return;
        }

        log.info("[비식재료 사전] dispute 카운터. keyword='{}', disputeCount={}, feedbackId={}",
                rejectionKeyword, disputeCount, event.feedbackId().getValue());

        // ── Step 2: Gate 1 — 최소 dispute 수 미달 ───────────────────
        if (disputeCount < REFExclusionRemovalPolicy.MIN_REMOVAL_COUNT) {
            enqueueExclusionReview(event, rejectionKeyword, correctedGroceryItemName, false);
            return;
        }

        // ── Step 3: Gate 3 — correctedGroceryItemName 이분화 검사 ───
        Gate3Result gate3 = evaluateGate3(event, rejectionKeyword, correctedGroceryItemName);
        if (!gate3.passToGate2()) {
            return; // 검수 큐 적재는 evaluateGate3 내부에서 처리
        }

        // ── Step 4: Gate 2 — dispute 비율 검사 ──────────────────────
        long acceptCount = redisCounter.getAcceptCount(rejectionKeyword);
        boolean policyMet = exclusionRemovalPolicy.isMet(disputeCount, acceptCount);

        log.info("[비식재료 사전] Gate 2 검사. keyword='{}', dispute={}, accept={}, ratio={}, met={}",
                rejectionKeyword, disputeCount, acceptCount,
                String.format("%.2f",
                        exclusionRemovalPolicy.calculateDisputeRatio(disputeCount, acceptCount)),
                policyMet);

        if (!policyMet) {
            enqueueExclusionReview(event, rejectionKeyword, correctedGroceryItemName, false);
            return;
        }

        // ── Step 5: 자동 제거 실행 ───────────────────────────────────
        autoRemoveFromExclusionDictionary(event, rejectionKeyword, correctedGroceryItemName,
                disputeCount, acceptCount);
    }

    /* ──────────────────── Gate 3 ──────────────────── */

    /**
     * Gate 3을 평가합니다.
     *
     * <h3>분기 결과</h3>
     * <ul>
     *   <li>{@code PASS}: GroceryItem DB에 존재 → Gate 2로 진행</li>
     *   <li>{@code INVALID_INPUT}: 유효하지 않은 식재료명
     *       → EXCLUSION_REMOVAL 검수 큐만 적재</li>
     *   <li>{@code UNREGISTERED_ITEM}: 유효하지만 DB에 없음
     *       → EXCLUSION_REMOVAL + NEW_GROCERY_ITEM 동시 적재</li>
     * </ul>
     */
    private Gate3Result evaluateGate3(REFNegativeFeedbackEvent event,
                                      String rejectionKeyword,
                                      String correctedGroceryItemName) {
        if (!isValidGroceryItemName(correctedGroceryItemName)) {
            log.info("[비식재료 사전] Gate 3 실패 — 유효하지 않은 식재료명. " +
                    "keyword='{}', correctedName='{}'", rejectionKeyword, correctedGroceryItemName);
            enqueueExclusionReview(event, rejectionKeyword, correctedGroceryItemName, false);
            return Gate3Result.INVALID_INPUT;
        }

        if (groceryItemExistencePort.existsByName(correctedGroceryItemName)) {
            log.debug("[비식재료 사전] Gate 3 통과. keyword='{}', correctedName='{}'",
                    rejectionKeyword, correctedGroceryItemName);
            return Gate3Result.PASS;
        }

        log.info("[비식재료 사전] Gate 3 — 유효한 식재료명이나 DB 미등록. " +
                "EXCLUSION_REMOVAL + NEW_GROCERY_ITEM 동시 적재. " +
                "keyword='{}', correctedName='{}'", rejectionKeyword, correctedGroceryItemName);

        enqueueExclusionReview(event, rejectionKeyword, correctedGroceryItemName, false);
        enqueueNewGroceryItemReview(event, correctedGroceryItemName, rejectionKeyword);

        return Gate3Result.UNREGISTERED_ITEM;
    }

    /**
     * 식재료명이 유효한지 기초 검사를 수행합니다.
     */
    private boolean isValidGroceryItemName(String name) {
        if (name == null || name.isBlank()) return false;
        if (name.length() > MAX_GROCERY_ITEM_NAME_LENGTH) return false;
        return name.matches(".*[가-힣a-zA-Z0-9].*");
    }

    /* ──────────────────── 자동 제거 ──────────────────── */

    /**
     * 비식재료 사전에서 키워드를 자동 제거합니다.
     *
     * <h3>처리 순서</h3>
     * <ol>
     *   <li>EXCLUSION 사전 조회 (없으면 경고 후 검수 큐 폴백)</li>
     *   <li>DB entry 제거 ({@code removeEntry})</li>
     *   <li>Redis Set 동기화 ({@code sync})</li>
     *   <li>커밋 후 Trie 재빌드 트리거 — {@link TransactionSynchronization#afterCommit()} 등록
     *       (트랜잭션 롤백 시 Trie 변경 방지)</li>
     *   <li>Redis 카운터 초기화</li>
     *   <li>감사 로그용 검수 항목 적재 ([AUTO_REMOVED] 마킹)</li>
     * </ol>
     */
    private void autoRemoveFromExclusionDictionary(REFNegativeFeedbackEvent event,
                                                   String rejectionKeyword,
                                                   String correctedGroceryItemName,
                                                   long disputeCount,
                                                   long acceptCount) {
        REFRecognitionDictionary exclusionDict = dictionaryRepository
                .findByDictType(REFRecognitionDictionaryType.EXCLUSION)
                .orElse(null);

        if (exclusionDict == null) {
            log.error("[비식재료 사전] EXCLUSION 사전 조회 실패, 검수 큐로 폴백. keyword='{}'",
                    rejectionKeyword);
            enqueueExclusionReview(event, rejectionKeyword, correctedGroceryItemName, false);
            return;
        }

        if (!exclusionDict.hasEntry(rejectionKeyword)) {
            log.warn("[비식재료 사전] 키워드가 이미 사전에 없음 (이미 제거됨?). keyword='{}'",
                    rejectionKeyword);
            redisCounter.resetCounters(rejectionKeyword);
            return;
        }

        exclusionDict.removeEntry(rejectionKeyword);
        dictionaryRepository.save(exclusionDict);

        redisSync.sync(exclusionDict);

        // [수정 1.2] eventPublisher.publishEvent()를 트랜잭션 커밋 후로 분리.
        // 기존 코드에서는 동기 @EventListener인 REFDictionarySyncEventHandler가
        // 같은 REQUIRES_NEW 트랜잭션 안에서 Trie를 재빌드했습니다.
        // 트랜잭션 롤백 시 DB는 원복되지만 Trie는 이미 변경된 채로 남아 불일치가 발생했습니다.
        // afterCommit()에서만 이벤트를 발행하여 DB ↔ Trie 일관성을 보장합니다.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publishEvent(
                                new REFDictionarySyncedEvent(REFRecognitionDictionaryType.EXCLUSION));
                        log.info("[비식재료 사전] 커밋 후 Trie 재빌드 이벤트 발행. keyword='{}'",
                                rejectionKeyword);
                    }
                }
        );

        redisCounter.resetCounters(rejectionKeyword);

        log.info("[비식재료 사전] 자동 제거 완료. keyword='{}', dispute={}, accept={}, ratio={}",
                rejectionKeyword, disputeCount, acceptCount,
                String.format("%.2f",
                        exclusionRemovalPolicy.calculateDisputeRatio(disputeCount, acceptCount)));

        enqueueExclusionReview(event, rejectionKeyword, correctedGroceryItemName, true);
    }

    /* ──────────────────── 검수 큐 적재 ──────────────────── */

    private void enqueueExclusionReview(REFNegativeFeedbackEvent event,
                                        String rejectionKeyword,
                                        String correctedGroceryItemName,
                                        boolean autoRemoved) {
        String autoTag = autoRemoved ? "[AUTO_REMOVED] " : "";
        String context = String.format(
                "%s원본제품명='%s', 사용자수정식재료='%s', 반려키워드='%s'",
                autoTag,
                event.snapshot().getProductName(),
                correctedGroceryItemName != null ? correctedGroceryItemName : "(미입력)",
                rejectionKeyword
        );

        reviewItemRepository.findByReviewTypeAndTargetValue(
                        REFReviewType.EXCLUSION_REMOVAL, rejectionKeyword)
                .ifPresentOrElse(
                        REFFeedbackReviewItem::incrementOccurrence,
                        () -> reviewItemRepository.save(
                                REFFeedbackReviewItem.create(
                                        REFReviewType.EXCLUSION_REMOVAL,
                                        rejectionKeyword,
                                        context,
                                        event.feedbackId().getValue()
                                )
                        )
                );

        log.info("[비식재료 사전] EXCLUSION_REMOVAL 검수 큐 적재. " +
                        "keyword='{}', autoRemoved={}, feedbackId={}",
                rejectionKeyword, autoRemoved, event.feedbackId().getValue());
    }

    private void enqueueNewGroceryItemReview(REFNegativeFeedbackEvent event,
                                             String correctedGroceryItemName,
                                             String relatedExclusionKeyword) {
        String context = String.format(
                "원본제품명='%s', 연관비식재료키워드='%s', completedBy='ExclusionFilter'",
                event.snapshot().getProductName(),
                relatedExclusionKeyword
        );

        reviewItemRepository.findByReviewTypeAndTargetValue(
                        REFReviewType.NEW_GROCERY_ITEM, correctedGroceryItemName)
                .ifPresentOrElse(
                        REFFeedbackReviewItem::incrementOccurrence,
                        () -> reviewItemRepository.save(
                                REFFeedbackReviewItem.create(
                                        REFReviewType.NEW_GROCERY_ITEM,
                                        correctedGroceryItemName,
                                        context,
                                        event.feedbackId().getValue(),
                                        "ExclusionFilter"
                                )
                        )
                );

        log.info("[비식재료 사전] NEW_GROCERY_ITEM 검수 큐 적재. " +
                        "correctedName='{}', relatedKeyword='{}', feedbackId={}",
                correctedGroceryItemName, relatedExclusionKeyword,
                event.feedbackId().getValue());
    }

    /* ──────────────────── Gate3Result ──────────────────── */

    /**
     * Gate 3 평가 결과.
     */
    private enum Gate3Result {
        /** GroceryItem DB에 존재 — Gate 2로 진행 */
        PASS(true),

        /** 유효하지 않은 식재료명 입력 — EXCLUSION_REMOVAL 검수 큐만 적재 */
        INVALID_INPUT(false),

        /** 유효하지만 DB 미등록 — EXCLUSION_REMOVAL + NEW_GROCERY_ITEM 동시 적재 */
        UNREGISTERED_ITEM(false);

        private final boolean passToGate2;

        Gate3Result(boolean passToGate2) {
            this.passToGate2 = passToGate2;
        }

        boolean passToGate2() {
            return passToGate2;
        }
    }
}