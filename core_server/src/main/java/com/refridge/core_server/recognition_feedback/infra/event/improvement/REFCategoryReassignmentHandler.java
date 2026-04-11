package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 카테고리가 변경된 부정 피드백을 처리합니다.
 *
 * <h3>검수 큐 적재 이유</h3>
 * <p>
 * 카테고리 변경은 GroceryItem의 분류 자체를 바꾸는 것이므로
 * 영향 범위가 넓어 자동 반영하지 않고 관리자 검수 큐에 적재합니다.
 * 관리자가 승인하면 {@code REFCategoryReassignmentApprovedEvent}가 발행되어
 * GroceryItem 및 Product의 카테고리를 갱신합니다.
 * </p>
 *
 * <h3>outlier 차단 (Hash + __total__ 구조)</h3>
 * <p>
 * alias/correction과 동일한 Hash + {@code __total__} 구조를 도입하여
 * 전체 반응 대비 수정 비율이 {@link #CATEGORY_REPORT_RATIO}(5%) 미만이면
 * 검수 큐에 적재하지 않습니다.
 * </p>
 * <p>
 * 5% 미만인 카테고리 수정은 outlier로 간주하여 검수 큐에 적재하지 않습니다.
 * 이는 1000명이 동의한 카테고리에 대해 3명만 수정한 경우 (3/1003=0.3%)
 * 관리자의 시간을 낭비하지 않기 위함입니다.
 * 비율 데이터가 부족한 초기 단계({@code __total__} {@code <} {@link #MIN_CATEGORY_REPORT})에서는
 * 기존과 동일하게 무조건 적재합니다.
 * </p>
 *
 * <h3>전체 처리 흐름</h3>
 * <pre>
 * REFNegativeFeedbackDispatcher
 *   │
 *   └─▶ handle(event)
 *         │
 *         ├─ [Step 1] targetValue, hashKey 구성
 *         │
 *         ├─ [Step 2] Redis Hash 카운터 증가
 *         │     ├─ HINCR: targetValue 선택 횟수 +1
 *         │     └─ HINCR: __total__ +1
 *         │
 *         ├─ [Step 3] Redis miss 복원
 *         │     └─ occurrenceCount == 1?
 *         │           → 검수 큐에서 기존 occurrenceCount 조회하여 Redis에 복원
 *         │             (__total__ 복원은 현 태스크 범위에서 정확도 손실 감수)
 *         │
 *         ├─ [Step 4] TTL 갱신 (30일)
 *         │
 *         ├─ [Step 5] outlier 비율 체크
 *         │     └─ occurrenceCount >= MIN_CATEGORY_REPORT AND
 *         │        occurrenceCount / __total__ < CATEGORY_REPORT_RATIO(5%)
 *         │           → return (검수 큐 적재 생략)
 *         │
 *         └─ [Step 6] 검수 큐 적재 (비율 정보 포함)
 * </pre>
 *
 * <h3>Redis 데이터 구조</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead>
 *     <tr style="background:#f0f0f0;">
 *       <th>키</th><th>타입</th><th>필드</th><th>TTL</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code feedback:category-reassignment:{originalCategoryPath}}</td>
 *       <td>Hash</td>
 *       <td>{targetValue}, {@code __total__}</td>
 *       <td>30일 (활동 시 갱신)</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>__total__ 증가 시점</h3>
 * <ul>
 *   <li>부정 피드백에서 카테고리를 수정한 경우: targetValue +1, __total__ +1 (이 핸들러)</li>
 *   <li>긍정 피드백: __total__ +1 (Task 4 — {@code REFPositiveFeedbackAggregationHandler})</li>
 *   <li>부정 피드백에서 카테고리를 수정하지 않은 경우(암묵적 긍정): __total__ +1 (Task 5)</li>
 * </ul>
 *
 * <h3>targetValue 형식</h3>
 * <pre>
 *   "{correctedGroceryItemName}::{correctedCategoryPath}"
 *   예: "두부::채소류 > 두부/묵류"
 * </pre>
 *
 * <h3>originalCategoryPath 결정</h3>
 * <p>
 * {@code event.snapshot().getCategoryPath()}를 사용합니다.
 * REJECTED 케이스 등 카테고리가 없는 경우 {@code "UNKNOWN"}으로 대체합니다.
 * </p>
 *
 * <h3>sourceHandlerName 기록</h3>
 * <p>
 * 카테고리 재분류 유형에서도 {@code sourceHandlerName}을 기록합니다.
 * 현재는 승인 분기에 영향을 주지 않으나, 향후 분석/통계 목적으로 활용 가능합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFNegativeFeedbackDispatcher
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFCategoryReassignmentHandler implements REFImprovementActionHandler {

    private final REFFeedbackReviewItemRepository reviewItemRepository;
    private final StringRedisTemplate redisTemplate;

    // ── 키 상수 ──────────────────────────────────────────────────────────────

    /** 카테고리 재분류 후보 Hash 키 접두사 ({@code feedback:category-reassignment:{originalCategoryPath}}) */
    private static final String CATEGORY_REASSIGNMENT_PREFIX = "feedback:category-reassignment:";

    /** Hash의 전체 반응 횟수를 저장하는 특수 필드명 */
    private static final String TOTAL_FIELD = "__total__";

    /** {@code getCategoryPath()}가 null인 경우(REJECTED 케이스 등) Hash 키 대체값 */
    private static final String UNKNOWN_CATEGORY = "UNKNOWN";

    /** Hash 키 TTL — 30일간 활동 없으면 자동 만료 */
    private static final Duration CANDIDATE_TTL = Duration.ofDays(30);

    // ── 임계값 상수 ──────────────────────────────────────────────────────────

    /**
     * 검수 큐 적재를 위한 최소 선택 횟수.
     * {@code __total__}이 이 값 미만이면 비율 계산 없이 무조건 적재합니다
     * (초기 단계에서는 5% 체크를 적용하면 정상 수정도 차단될 수 있으므로).
     */
    static final int MIN_CATEGORY_REPORT = 3;

    /**
     * 검수 큐 적재를 위한 최소 지지율 (선택 횟수 / {@code __total__}).
     * 이 비율 미만이면 전체 반응 대비 극소수이므로 outlier로 간주하여 검수 큐에 적재하지 않습니다.
     */
    static final double CATEGORY_REPORT_RATIO = 0.05;

    // ── 진입점 ───────────────────────────────────────────────────────────────

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.CATEGORY;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String correctedCategory = event.correction().getCorrectedCategoryPath();
        if (correctedCategory == null || correctedCategory.isBlank()) return;

        // ── Step 1: targetValue, hashKey 구성 ────────────────────────────────
        String targetValue = buildTargetValue(event);
        String completedBy = event.snapshot().getCompletedBy();

        String originalCategoryPath = event.snapshot().getCategoryPath();
        String hashKey = CATEGORY_REASSIGNMENT_PREFIX
                + (originalCategoryPath != null && !originalCategoryPath.isBlank()
                ? originalCategoryPath
                : UNKNOWN_CATEGORY);

        // ── Step 2: Redis Hash 카운터 증가 ────────────────────────────────────
        Long occurrenceCount = redisTemplate.opsForHash().increment(hashKey, targetValue, 1);
        Long totalCount = redisTemplate.opsForHash().increment(hashKey, TOTAL_FIELD, 1);

        if (occurrenceCount == null || totalCount == null) {
            log.warn("[카테고리 재분류] Redis HINCR 실패, 검수 큐 폴백. targetValue='{}'", targetValue);
            enqueueForReview(event, targetValue, completedBy, 1, 0);
            return;
        }

        // ── Step 3: Redis miss 복원 ───────────────────────────────────────────
        // occurrenceCount == 1이면 TTL 만료 후 재진입 가능성.
        // 검수 큐의 기존 occurrenceCount를 Redis에 복원합니다.
        // __total__ 복원은 별도 Repository 쿼리 없이 현 태스크에서는 정확도 손실을 감수합니다.
        if (occurrenceCount == 1) {
            occurrenceCount = restoreOccurrenceFromReviewQueue(hashKey, targetValue);
        }

        // ── Step 4: TTL 갱신 ──────────────────────────────────────────────────
        // 복원 이후에 실행하여 복원 직후 TTL이 즉시 세팅되도록 합니다.
        redisTemplate.expire(hashKey, CANDIDATE_TTL);

        log.info("[카테고리 재분류] targetValue='{}', occurrenceCount={}, totalCount={}, feedbackId={}",
                targetValue, occurrenceCount, totalCount, event.feedbackId().getValue());

        // ── Step 5: outlier 비율 체크 ─────────────────────────────────────────
        // __total__ < MIN_CATEGORY_REPORT인 초기 단계에서는 비율 체크를 건너뜁니다.
        // 비율 체크는 충분한 모수가 확보된 이후에만 의미가 있기 때문입니다.
        if (occurrenceCount >= MIN_CATEGORY_REPORT && totalCount >= MIN_CATEGORY_REPORT) {
            double ratio = (double) occurrenceCount / totalCount;
            if (ratio < CATEGORY_REPORT_RATIO) {
                log.info("[카테고리 재분류] outlier 차단. targetValue='{}', ratio={}/{} = {:.2f}% < {:.0f}%",
                        targetValue, occurrenceCount, totalCount,
                        ratio * 100, CATEGORY_REPORT_RATIO * 100);
                return;
            }
        }

        // ── Step 6: 검수 큐 적재 ─────────────────────────────────────────────
        enqueueForReview(event, targetValue, completedBy, occurrenceCount, totalCount);
    }

    /**
     * Redis miss 복원 — 검수 큐에서 기존 {@code occurrenceCount}를 조회하여
     * Redis Hash에 복원합니다.
     *
     * <p>
     * 카테고리 수정은 alias/correction보다 빈도가 낮아 TTL 만료 가능성이 높습니다.
     * alias/correction이 별도 DB 테이블에서 정확한 집계를 복원하는 것과 달리,
     * 카테고리 재분류는 검수 큐의 {@code occurrenceCount}를 대리 지표로 활용합니다.
     * {@code __total__}은 현 태스크 범위에서 정확도 손실을 감수합니다.
     * </p>
     *
     * <h3>케이스 분기</h3>
     * <ul>
     *   <li><b>Case A (진짜 첫 피드백):</b> 검수 큐에 이력 없음 → 1 그대로 반환</li>
     *   <li><b>Case B (TTL 만료 후 재진입):</b> 검수 큐에 기존 occurrenceCount 존재
     *       → Redis Hash에 복원 후 복원된 값 반환</li>
     * </ul>
     *
     * @param hashKey     Redis Hash 키
     * @param targetValue 복원할 targetValue 필드명
     * @return 복원 후 occurrenceCount. 진짜 첫 피드백이면 1
     */
    private long restoreOccurrenceFromReviewQueue(String hashKey, String targetValue) {
        return reviewItemRepository
                .findByReviewTypeAndTargetValue(REFReviewType.CATEGORY_REASSIGNMENT, targetValue)
                .map(existing -> {
                    // 검수 큐의 occurrenceCount는 이번 피드백 이전까지의 누적값
                    // 이번 HINCR로 이미 1이 세팅된 상태이므로, DB 값 + 1을 복원값으로 설정
                    long restored = existing.getOccurrenceCount() + 1L;
                    redisTemplate.opsForHash().put(
                            hashKey, targetValue, String.valueOf(restored));
                    log.info("[카테고리 재분류] Redis 복원. targetValue='{}', restored={}",
                            targetValue, restored);
                    return restored;
                })
                .orElse(1L); // Case A: 진짜 첫 피드백
    }

    /**
     * 검수 큐에 적재합니다.
     * <p>
     * 동일한 {@code targetValue}가 이미 존재하면 {@code occurrenceCount}만 증가시킵니다.
     * {@code contextDetail}에 비율 정보를 포함하여 관리자가 우선순위를 판단할 수 있도록 합니다.
     *
     * @param event           부정 피드백 이벤트
     * @param targetValue     검수 항목 유니크 키
     * @param completedBy     인식 파이프라인 핸들러명
     * @param occurrenceCount 현재 선택 횟수 (비율 계산용)
     * @param totalCount      전체 반응 횟수 (비율 계산용, 0이면 비율 미표시)
     */
    private void enqueueForReview(REFNegativeFeedbackEvent event,
                                  String targetValue,
                                  String completedBy,
                                  long occurrenceCount,
                                  long totalCount) {

        String correctedCategory = event.correction().getCorrectedCategoryPath();
        String context = buildContext(event, correctedCategory, occurrenceCount, totalCount);

        reviewItemRepository.findByReviewTypeAndTargetValue(
                        REFReviewType.CATEGORY_REASSIGNMENT, targetValue)
                .ifPresentOrElse(
                        REFFeedbackReviewItem::incrementOccurrence,
                        () -> reviewItemRepository.save(
                                REFFeedbackReviewItem.create(
                                        REFReviewType.CATEGORY_REASSIGNMENT,
                                        targetValue,
                                        context,
                                        event.feedbackId().getValue(),
                                        completedBy
                                )
                        )
                );

        log.info("[카테고리 재분류] 검수 큐 적재. targetValue='{}', occurrenceCount={}, " +
                        "totalCount={}, completedBy={}, feedbackId={}",
                targetValue, occurrenceCount, totalCount,
                completedBy, event.feedbackId().getValue());
    }

    /**
     * 검수 항목의 {@code contextDetail}을 구성합니다.
     * <p>
     * 비율 정보를 포함하여 관리자가 수정 빈도와 전체 반응 대비 비중을 한눈에 파악할 수 있도록 합니다.
     */
    private String buildContext(REFNegativeFeedbackEvent event,
                                String correctedCategory,
                                long occurrenceCount,
                                long totalCount) {
        String ratioInfo = totalCount > 0
                ? String.format("%d/%d(%.1f%%)", occurrenceCount, totalCount,
                (double) occurrenceCount / totalCount * 100)
                : String.format("%d/-(집계중)", occurrenceCount);

        return String.format(
                "원본카테고리='%s', 수정카테고리='%s', 비율=%s, 식재료변경=%s, " +
                        "원본식재료='%s', 원본제품명='%s', completedBy='%s'",
                event.snapshot().getCategoryPath(),
                correctedCategory,
                ratioInfo,
                event.diff().isGroceryItemChanged(),
                event.snapshot().getGroceryItemName(),
                event.snapshot().getProductName(),
                event.snapshot().getCompletedBy()
        );
    }

    /**
     * 검수 항목의 유니크 키({@code targetValue})를 구성합니다.
     * <p>
     * 형식: "{correctedGroceryItemName}::{correctedCategoryPath}"
     * 동시에 식재료명이 변경된 경우 수정된 식재료명을 사용합니다.
     */
    private String buildTargetValue(REFNegativeFeedbackEvent event) {
        String groceryItem = event.diff().isGroceryItemChanged()
                ? event.correction().getCorrectedGroceryItemName()
                : event.snapshot().getGroceryItemName();
        return groceryItem + "::" + event.correction().getCorrectedCategoryPath();
    }
}