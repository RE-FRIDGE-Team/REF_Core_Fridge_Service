package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackGroceryItemMappingCountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 식재료명이 변경된 부정 피드백을 처리하는 개선 핸들러입니다.
 *
 * <h3>핵심 역할 — 식재료명 교정 투표 집계 및 자동 확정</h3>
 * <p>
 * {@link REFProductNameAliasHandler}가 제품명 alias를 관리하듯,
 * 이 핸들러는 "파이프라인 인식 식재료명 → 사용자 교정 식재료명" 투표를
 * Redis Hash에 누적하고 3중 게이트 조건 충족 시 {@link REFGroceryItemCorrectionService}에
 * 확정을 위임합니다.
 * </p>
 *
 * <h3>전체 처리 흐름</h3>
 * <pre>
 * REFNegativeFeedbackDispatcher
 *   │
 *   └─▶ handle(event)
 *         │
 *         ├─ [Guard] REJECTED_BUT_FOOD 동시 트리거 시 조기 리턴 (이슈 2.3)
 *         │
 *         ├─ [Step 1] incrementCandidateCounts()
 *         │     ├─ Redis HINCR: correctedGroceryItemName 선택 횟수 +1
 *         │     └─ Redis HINCR: __total__ +1
 *         │
 *         ├─ [Step 2] restoreFromDbIfMiss()
 *         │     └─ occurrenceCount == 1?
 *         │           ├─ Case A (첫 피드백): DB 비어있음 → 그대로 진행
 *         │           └─ Case B (TTL 만료):  DB 이력 존재 → Hash 전체 복원
 *         │                 __total__ = CORRECTED 합계 + APPROVED 합계 (이슈 2.2)
 *         │
 *         ├─ [Step 3] TTL 갱신 (30일)
 *         │
 *         ├─ [Step 4] enqueueForReview() — 신규 식재료 가능성 검수 큐 적재
 *         │
 *         └─ [Step 5] evaluateAndConfirmCorrection()
 *               ├─ Gate 1 빠른 차단: occurrenceCount < MIN_CORRECTION_COUNT(10)
 *               ├─ HGETALL → Gate 2, Gate 3 검사
 *               ├─ 통과 → REFGroceryItemCorrectionService.confirmCorrection()
 *               │           └─ DB 저장 + Redis 반영 + 이벤트 발행
 *               └─ 미달 + CONFIRMED → reopenCorrection()
 * </pre>
 *
 * <h3>REJECTED_BUT_FOOD 동시 트리거 방지 (이슈 2.3)</h3>
 * <p>
 * REJECTED 상태 인식 결과에 대해 사용자가 {@code correctedGroceryItemName}을 입력하면
 * {@code changedFields}에 {@code REJECTED_BUT_FOOD}와 {@code GROCERY_ITEM}이 동시에 추가됩니다.
 * 이 경우 {@link REFExclusionRemovalHandler}가 이미 NEW_GROCERY_ITEM 검수 큐를 적재했으므로,
 * 이 핸들러까지 동일 {@code correctedGroceryItemName}으로 검수 큐를 적재하면
 * {@code occurrenceCount}가 실제보다 1 과대 집계됩니다.
 * handle() 진입부에서 {@code isRejectedButFood()} guard를 추가하여 중복 처리를 방지합니다.
 * </p>
 *
 * <h3>Redis 데이터 구조</h3>
 * <pre>
 *   Hash: feedback:grocery-item-correction:{originalGroceryItemName}
 *     Field: {correctedName1} → 선택 횟수
 *     Field: __total__        → 전체 반응 횟수 (긍정 피드백 포함)
 *     TTL: 30일
 * </pre>
 *
 * <h3>__total__ 관리 분담</h3>
 * <ul>
 *   <li>부정 피드백(이 핸들러): 수정본 선택 시 correctedName +1, __total__ +1</li>
 *   <li>긍정 피드백({@link REFPositiveFeedbackAggregationHandler}): 승인 시 __total__ +1</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 * @see REFGroceryItemCorrectionService
 * @see REFPositiveFeedbackAggregationHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemMappingHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFFeedbackReviewItemRepository reviewItemRepository;
    private final REFGroceryItemCorrectionService correctionService;
    private final REFRecognitionFeedbackRepository feedbackRepository;

    private static final Duration CANDIDATE_TTL = Duration.ofDays(30);

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.GROCERY_ITEM;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        // REJECTED_BUT_FOOD와 GROCERY_ITEM이 동시에 changedFields에 추가되는 케이스 방지.
        // REFExclusionRemovalHandler가 이미 NEW_GROCERY_ITEM 검수 큐를 적재하고 카운터를 처리했으므로
        // 이 핸들러에서 중복 처리하면 occurrenceCount가 실제보다 1 과대 집계됩니다.
        if (event.diff().isRejectedButFood()) {
            log.debug("[식재료명 교정] REJECTED_BUT_FOOD 동시 트리거 — ExclusionRemovalHandler가 이미 처리. " +
                    "feedbackId={}", event.feedbackId().getValue());
            return;
        }

        String originalGroceryItem = event.snapshot().getGroceryItemName();
        String correctedGroceryItem = event.correction().getCorrectedGroceryItemName();
        String completedBy = event.snapshot().getCompletedBy();

        if (correctedGroceryItem == null || correctedGroceryItem.isBlank()) return;
        if (correctedGroceryItem.equals(originalGroceryItem)) return;

        String hashKey = REFGroceryItemCorrectionService.CORRECTION_CANDIDATE_PREFIX
                + originalGroceryItem;

        // Step 1: Redis Hash 카운터 증가
        CandidateCounts counts = incrementCandidateCounts(hashKey, correctedGroceryItem);
        if (counts == null) return;

        // Step 2: TTL 만료 후 재진입이면 DB에서 Hash 복원
        counts = restoreFromDbIfMiss(hashKey, originalGroceryItem, correctedGroceryItem, counts);

        // Step 3: TTL 갱신 (restoreFromDbIfMiss 이후 실행하여 복원 직후 TTL 즉시 세팅)
        redisTemplate.expire(hashKey, CANDIDATE_TTL);

        log.info("[식재료명 교정] '{}' → '{}', 이 후보={}회, 전체={}회, feedbackId={}",
                originalGroceryItem, correctedGroceryItem,
                counts.occurrenceCount(), counts.totalCount(),
                event.feedbackId().getValue());

        // Step 4: 신규 식재료 가능성 검수 큐 적재
        enqueueForReview(event, correctedGroceryItem, completedBy);

        // Step 5: 3중 게이트 검사 및 교정 확정/reopen
        evaluateAndConfirmCorrection(hashKey, originalGroceryItem, correctedGroceryItem, counts);
    }

    /**
     * Redis Hash에 수정본 선택 횟수와 전체 반응 횟수를 각각 1씩 증가시킵니다.
     */
    private CandidateCounts incrementCandidateCounts(String hashKey, String correctedGroceryItem) {
        Long occurrenceCount = redisTemplate.opsForHash()
                .increment(hashKey, correctedGroceryItem, 1);
        Long totalCount = redisTemplate.opsForHash()
                .increment(hashKey, REFGroceryItemCorrectionService.TOTAL_FIELD, 1);

        if (occurrenceCount == null || totalCount == null) {
            log.warn("[식재료명 교정] Redis increment 실패. hashKey='{}'", hashKey);
            return null;
        }

        return new CandidateCounts(occurrenceCount, totalCount);
    }

    /**
     * TTL 만료 후 첫 접근인 경우 DB에서 교정 이력을 조회하여 Hash를 복원합니다.
     *
     * <h3>케이스 분기</h3>
     * <ul>
     *   <li><b>Case A (진짜 첫 피드백):</b> DB가 비어있음 → 복원 없이 그대로 반환</li>
     *   <li><b>Case B (TTL 만료 후 재진입):</b> DB에 이전 이력 존재 → Hash 전체 복원 후 반환</li>
     * </ul>
     *
     * <h3>[이슈 2.2 수정] __total__ 복원 시 APPROVED 합산</h3>
     * <p>
     * 기존에는 {@code __total__}을 CORRECTED 피드백 합계로만 복원했습니다.
     * 수정 후에는 APPROVED(긍정 피드백) 건수도 조회하여 합산함으로써
     * Gate 2 비율의 과대 계산 문제를 해결합니다.
     * </p>
     */
    private CandidateCounts restoreFromDbIfMiss(String hashKey,
                                                String originalGroceryItem,
                                                String correctedGroceryItem,
                                                CandidateCounts counts) {
        if (counts.occurrenceCount() != 1) return counts;

        List<REFFeedbackGroceryItemMappingCountDto> dbCounts =
                feedbackRepository.findGroceryItemMappingCountsByOriginalName(originalGroceryItem);

        if (dbCounts.isEmpty()) return counts;

        long dbTotal = 0L;
        for (REFFeedbackGroceryItemMappingCountDto dto : dbCounts) {
            redisTemplate.opsForHash().put(
                    hashKey,
                    dto.correctedGroceryItemName(),
                    String.valueOf(dto.selectionCount())
            );
            dbTotal += dto.selectionCount();
        }

        // [수정 2.2] APPROVED(긍정 피드백) 건수를 __total__에 합산하여 Gate 2 비율 정확성 보장.
        // 기존: __total__ = CORRECTED 합계 → 긍정 피드백 누락으로 비율 과대 계산
        // 수정: __total__ = CORRECTED 합계 + APPROVED 합계
        long approvedCount = feedbackRepository.countApprovedByOriginalGroceryItemName(originalGroceryItem);
        long restoredTotal = dbTotal + approvedCount;

        redisTemplate.opsForHash().put(
                hashKey,
                REFGroceryItemCorrectionService.TOTAL_FIELD,
                String.valueOf(restoredTotal)
        );

        long restoredOccurrence = dbCounts.stream()
                .filter(d -> correctedGroceryItem.equals(d.correctedGroceryItemName()))
                .mapToLong(REFFeedbackGroceryItemMappingCountDto::selectionCount)
                .findFirst()
                .orElse(1L);

        log.info("[식재료명 교정] Redis 복원. originalGroceryItem='{}', 후보수={}, correctedTotal={}, " +
                        "approvedCount={}, restoredTotal={}",
                originalGroceryItem, dbCounts.size(), dbTotal, approvedCount, restoredTotal);

        return new CandidateCounts(restoredOccurrence, restoredTotal);
    }

    /**
     * 3중 게이트를 검사하여 교정 확정 또는 reopen을 처리합니다.
     */
    private void evaluateAndConfirmCorrection(String hashKey,
                                              String originalGroceryItem,
                                              String correctedGroceryItem,
                                              CandidateCounts counts) {
        if (counts.occurrenceCount() < REFGroceryItemCorrectionService.MIN_CORRECTION_COUNT) {
            return;
        }

        Map<String, Long> allCounts = correctionService.getAllCandidateCounts(hashKey);

        boolean meetsThreshold = correctionService.meetsConfirmationThreshold(
                correctedGroceryItem,
                counts.occurrenceCount(),
                counts.totalCount(),
                allCounts
        );

        if (meetsThreshold) {
            correctionService.confirmCorrection(
                    originalGroceryItem,
                    correctedGroceryItem,
                    counts.occurrenceCount(),
                    counts.totalCount()
            );

        } else if (correctionService.isConfirmed(originalGroceryItem)) {
            correctionService.reopenCorrection(originalGroceryItem);
            log.info("[식재료명 교정] 경쟁 후보 재부상, reopen. originalGroceryItem='{}'",
                    originalGroceryItem);
        }
    }

    /**
     * 신규 식재료 후보를 검수 큐에 적재합니다.
     *
     * <p>
     * 이미 동일한 {@code correctedGroceryItem}이 존재하면 {@code occurrenceCount}만 증가시킵니다.
     * {@code sourceHandlerName}을 기록하여 관리자 승인 시 MLPrediction 여부 판단에 활용합니다.
     * </p>
     */
    private void enqueueForReview(REFNegativeFeedbackEvent event,
                                  String correctedGroceryItem,
                                  String completedBy) {
        String context = String.format(
                "원본식재료='%s', 수정식재료='%s', completedBy='%s', " +
                        "카테고리동시변경=%s, 원본제품명='%s'",
                event.snapshot().getGroceryItemName(),
                correctedGroceryItem,
                completedBy,
                event.diff().isCategoryChanged(),
                event.snapshot().getProductName()
        );

        reviewItemRepository.findByReviewTypeAndTargetValue(
                        REFReviewType.NEW_GROCERY_ITEM, correctedGroceryItem)
                .ifPresentOrElse(
                        REFFeedbackReviewItem::incrementOccurrence,
                        () -> reviewItemRepository.save(
                                REFFeedbackReviewItem.create(
                                        REFReviewType.NEW_GROCERY_ITEM,
                                        correctedGroceryItem,
                                        context,
                                        event.feedbackId().getValue(),
                                        completedBy
                                )
                        )
                );
    }

    /**
     * Redis HINCR 결과로 얻은 수정본 선택 횟수와 전체 반응 횟수를 함께 전달하기 위한
     * 불변 값 객체입니다.
     */
    private record CandidateCounts(long occurrenceCount, long totalCount) {}
}