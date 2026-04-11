package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import com.refridge.core_server.recognition_feedback.infra.brand.REFBrandDictionaryFlushService;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackBrandCorrectionCountByOriginalDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 브랜드명이 변경된 부정 피드백을 처리하는 개선 핸들러입니다.
 *
 * <h3>진입 경로</h3>
 * <p>
 * {@code REFNegativeFeedbackDispatcher}가 {@code REFCorrectionDiff}를 분석하여
 * {@code changedFields}에 {@link REFCorrectionType#BRAND}가 포함된 경우
 * 이 핸들러의 {@link #handle(REFNegativeFeedbackEvent)}를 호출합니다.
 * </p>
 *
 * <h3>트랙 분기</h3>
 * <p>
 * 원본 스냅샷의 브랜드 유무에 따라 두 트랙으로 분기합니다.
 * </p>
 *
 * <h4>트랙 1 — 신규 브랜드 추가 (원본 브랜드 없음)</h4>
 * <p>
 * 파이프라인이 브랜드를 추출하지 못한 케이스입니다. 사용자가 입력한 브랜드를
 * 단순 카운터({@code feedback:brand:{correctedBrand}})로 집계하고
 * {@link #MIN_BRAND_INPUT_NEW}(5회) 이상이면 PENDING에 추가합니다.
 * 기존 매칭에 해를 끼치지 않는 additive 개선입니다.
 * </p>
 *
 * <h4>트랙 2 — 기존 브랜드 교체 (원본 브랜드 존재)</h4>
 * <p>
 * 파이프라인의 브랜드 추출이 틀렸다는 사용자 주장입니다. 충분한 근거 없이 반영하면
 * 위험하므로, alias/correction과 동일한 Hash + {@code __total__} 구조로 집계하여
 * 3중 게이트를 모두 통과한 경우에만 PENDING에 추가합니다.
 * </p>
 *
 * <h3>트랙 1 Redis 구조</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead><tr><th>키</th><th>타입</th><th>TTL</th></tr></thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code feedback:brand:{correctedBrand}}</td>
 *       <td>String</td>
 *       <td>30일 (활동 시 갱신)</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>트랙 2 Redis 구조</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead><tr><th>키</th><th>타입</th><th>필드</th><th>TTL</th></tr></thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code feedback:brand-correction:{originalBrand}}</td>
 *       <td>Hash</td>
 *       <td>{correctedBrand}, {@code __total__}</td>
 *       <td>30일 (활동 시 갱신)</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>트랙 2 사전 추가 3중 게이트</h3>
 * <ul>
 *   <li>Gate 1: correctedBrand 선택 횟수 &ge; {@link #MIN_BRAND_CORRECTION}(10)</li>
 *   <li>Gate 2: 선택 횟수 / {@code __total__} &ge; {@link #BRAND_CORRECTION_RATIO}(0.7)</li>
 *   <li>Gate 3: 선택 횟수 / 2위 후보 횟수 &ge; {@link #BRAND_CORRECTION_DOMINANCE}(3.0)
 *       (2위 후보 없으면 자동 통과)</li>
 * </ul>
 *
 * <h3>__total__ 증가 시점 (트랙 2)</h3>
 * <ul>
 *   <li>부정 피드백에서 브랜드를 교체한 경우: correctedBrand +1, __total__ +1 (이 핸들러)</li>
 *   <li>긍정 피드백: __total__ +1 (Task 4 — {@code REFPositiveFeedbackAggregationHandler})</li>
 *   <li>부정 피드백에서 브랜드를 수정하지 않은 경우(암묵적 긍정): __total__ +1 (Task 5)</li>
 * </ul>
 *
 * <h3>TTL 만료 후 Redis miss 복원 전략</h3>
 * <ul>
 *   <li>트랙 1: {@code INCR} 결과 1 → DB {@code countByCorrectBrandName()} 조회 후 String SET</li>
 *   <li>트랙 2: {@code HINCR} 결과 1 → {@code findBrandCorrectionCountsByOriginalBrand()} +
 *       {@code countApprovedByOriginalBrandName()} 조회 후 Hash 전체 재구성.
 *       {@code __total__} = CORRECTED 합계 + APPROVED 합계
 *       (CORRECTED만으로 복원하면 Gate 2 비율 과대 계산 — {@code REFProductNameAliasHandler} 이슈 2.2와 동일)</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFNegativeFeedbackDispatcher
 * @see REFBrandDictionaryFlushService
 * @see com.refridge.core_server.recognition_feedback.infra.brand.REFBrandDictionaryScheduler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFBrandImprovementHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFBrandDictionaryFlushService brandDictionaryFlushService;
    private final REFRecognitionFeedbackRepository feedbackRepository;

    // ── 키 접두사 ────────────────────────────────────────────────────────────

    /** 트랙 1: 신규 브랜드 단순 카운터 키 접두사 ({@code feedback:brand:{correctedBrand}}) */
    private static final String BRAND_COUNTER_PREFIX = "feedback:brand:";

    /** 트랙 2: 기존 브랜드 교체 Hash 키 접두사 ({@code feedback:brand-correction:{originalBrand}}) */
    private static final String BRAND_CORRECTION_PREFIX = "feedback:brand-correction:";

    /** 트랙 2 Hash의 전체 반응 횟수를 저장하는 특수 필드명 */
    private static final String TOTAL_FIELD = "__total__";

    // ── 임계값 상수 ──────────────────────────────────────────────────────────

    /**
     * 트랙 1 임계값 — 파이프라인이 브랜드를 추출하지 못한 경우 사전 추가 기준.
     * 기존 {@code MIN_BRAND_COUNT}(2)에서 상향.
     */
    static final int MIN_BRAND_INPUT_NEW = 5;

    /** 트랙 2 Gate 1: 교체 브랜드 절대 샘플 수 최솟값 */
    static final int MIN_BRAND_CORRECTION = 10;

    /** 트랙 2 Gate 2: 교체 브랜드 지지율 (선택 횟수 / __total__) */
    static final double BRAND_CORRECTION_RATIO = 0.7;

    /** 트랙 2 Gate 3: 1위 후보 대비 2위 후보 우위 배율 */
    static final double BRAND_CORRECTION_DOMINANCE = 3.0;

    /**
     * PENDING Set이 이 크기에 도달하면 즉시 flush를 트리거합니다.
     * 그 외에는 {@code REFBrandDictionaryScheduler}가 매일 새벽 3시에 처리합니다.
     */
    private static final int BATCH_SIZE = 20;

    /** 카운터/Hash 키 공통 TTL. 피드백 발생 시마다 리셋됩니다. */
    private static final Duration COUNTER_TTL = Duration.ofDays(30);

    // ── 진입점 ───────────────────────────────────────────────────────────────

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.BRAND;
    }

    /**
     * 브랜드명 수정 피드백을 처리합니다.
     * <p>
     * 원본 스냅샷의 브랜드 유무에 따라 트랙 1(신규 추가) 또는 트랙 2(교체)로 분기합니다.
     * 수정된 브랜드명이 null 또는 공백이면 처리를 생략합니다.
     *
     * @param event 부정 피드백 도메인 이벤트
     */
    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String originalBrand = event.snapshot().getBrandName();
        String correctedBrand = event.correction().getCorrectedBrandName();

        if (correctedBrand == null || correctedBrand.isBlank()) return;

        if (originalBrand == null || originalBrand.isBlank()) {
            handleNewBrandAddition(correctedBrand, event);
        } else if (!originalBrand.equals(correctedBrand)) {
            handleBrandCorrection(originalBrand, correctedBrand, event);
        }
    }

    // ── 트랙 1: 신규 브랜드 추가 ────────────────────────────────────────────

    /**
     * 트랙 1 처리 — 파이프라인이 브랜드를 추출하지 못한 경우.
     * <p>
     * 기존 {@code handle()} 로직과 동일하나 임계값이 {@link #MIN_BRAND_INPUT_NEW}(5)로 상향됩니다.
     * 단순 카운터({@code feedback:brand:{correctedBrand}})를 증가시키고
     * 임계값 이상이면 PENDING에 추가합니다.
     */
    private void handleNewBrandAddition(String correctedBrand, REFNegativeFeedbackEvent event) {
        String counterKey = BRAND_COUNTER_PREFIX + correctedBrand;

        // ── Step 1: 카운터 증가 ───────────────────────────────────
        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count == null) {
            log.warn("[브랜드 핸들러][트랙1] Redis increment 실패. brand='{}'", correctedBrand);
            return;
        }

        // ── Step 2: Redis miss 복원 ───────────────────────────────
        // count == 1이면 Case A(첫 피드백) 또는 Case B(TTL 만료 후 재진입)
        if (count == 1) {
            long dbCount = feedbackRepository.countByCorrectBrandName(correctedBrand);
            if (dbCount > 1) {
                redisTemplate.opsForValue().set(counterKey, String.valueOf(dbCount));
                count = dbCount;
                log.info("[브랜드 핸들러][트랙1] Redis 복원. brand='{}', dbCount={}",
                        correctedBrand, dbCount);
            }
            // Case A: dbCount <= 1 → 진짜 첫 피드백, 복원 없이 진행
        }

        // ── Step 3: TTL 갱신 ──────────────────────────────────────
        redisTemplate.expire(counterKey, COUNTER_TTL);

        log.info("[브랜드 핸들러][트랙1] brand='{}', count={}, feedbackId={}",
                correctedBrand, count, event.feedbackId().getValue());

        if (count < MIN_BRAND_INPUT_NEW) return;

        // ── Step 4: PENDING 추가 ──────────────────────────────────
        Long pendingSize = brandDictionaryFlushService.addToPending(correctedBrand);
        log.info("[브랜드 핸들러][트랙1] PENDING 추가. brand='{}', pendingSize={}",
                correctedBrand, pendingSize);

        // ── Step 5: BATCH_SIZE 즉시 flush ─────────────────────────
        if (pendingSize != null && pendingSize >= BATCH_SIZE) {
            log.info("[브랜드 핸들러][트랙1] BATCH_SIZE({}) 도달, 즉시 flush.", BATCH_SIZE);
            brandDictionaryFlushService.flush();
        }
    }

    // ── 트랙 2: 기존 브랜드 교체 ────────────────────────────────────────────

    /**
     * 트랙 2 처리 — 파이프라인이 기존 브랜드를 틀리게 추출한 경우.
     * <p>
     * Hash({@code feedback:brand-correction:{originalBrand}})에
     * {@code correctedBrand} 필드와 {@code __total__} 필드를 각각 +1합니다.
     * 3중 게이트를 모두 통과하면 PENDING에 추가합니다.
     * <p>
     * 기존 브랜드를 ACTIVE에서 제거하는 것은 별도 검수 큐에서 처리합니다(현 태스크 범위 밖).
     */
    private void handleBrandCorrection(
            String originalBrand,
            String correctedBrand,
            REFNegativeFeedbackEvent event) {

        String hashKey = BRAND_CORRECTION_PREFIX + originalBrand;

        // ── Step 1: correctedBrand, __total__ 동시 증가 ──────────────────
        Long correctedCount = redisTemplate.opsForHash().increment(hashKey, correctedBrand, 1);
        redisTemplate.opsForHash().increment(hashKey, TOTAL_FIELD, 1);

        if (correctedCount == null) {
            log.warn("[브랜드 핸들러][트랙2] Redis HINCR 실패. original='{}', corrected='{}'",
                    originalBrand, correctedBrand);
            return;
        }

        // ── Step 2: Redis miss 복원 ──────────────────────────────────────
        // correctedCount == 1이면 이 필드가 처음 생성된 것
        // → TTL 만료 후 재진입일 경우 Hash 전체를 DB에서 재구성
        if (correctedCount == 1) {
            correctedCount = restoreBrandCorrectionFromDb(
                    hashKey, originalBrand, correctedBrand);
        }

        // ── Step 3: TTL 갱신 ────────────────────────────────────────────
        redisTemplate.expire(hashKey, COUNTER_TTL);

        log.info("[브랜드 핸들러][트랙2] original='{}', corrected='{}', correctedCount={}, feedbackId={}",
                originalBrand, correctedBrand, correctedCount, event.feedbackId().getValue());

        // ── Step 4: Gate 1 빠른 차단 ────────────────────────────────────
        if (correctedCount < MIN_BRAND_CORRECTION) return;

        // ── Step 5: Gate 2, 3 — HGETALL 후 검사 ────────────────────────
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(hashKey);
        if (!meetsBrandCorrectionGates(correctedBrand, correctedCount, entries)) return;

        // ── Step 6: PENDING 추가 + flush 트리거 ─────────────────────────
        Long pendingSize = brandDictionaryFlushService.addToPending(correctedBrand);
        log.info("[브랜드 핸들러][트랙2] PENDING 추가. corrected='{}', pendingSize={}",
                correctedBrand, pendingSize);

        if (pendingSize != null && pendingSize >= BATCH_SIZE) {
            log.info("[브랜드 핸들러][트랙2] BATCH_SIZE({}) 도달, 즉시 flush.", BATCH_SIZE);
            brandDictionaryFlushService.flush();
        }
    }

    /**
     * 트랙 2의 Gate 2, Gate 3을 검사합니다.
     * <p>
     * Gate 1({@link #MIN_BRAND_CORRECTION} 체크)은 호출 측에서 이미 통과 보장됩니다.
     * <p>
     * Gate 3에서 2위 후보가 없거나 0이면 자동 통과합니다.
     * (단일 후보만 존재하는 경우 우위 비교 자체가 무의미하므로 Gate 1·2가 충분한 방어막)
     *
     * @param correctedBrand  검사 대상 교체 브랜드명
     * @param correctedCount  검사 대상 교체 브랜드의 선택 횟수 (Gate 1 통과 보장)
     * @param entries         Hash 전체 엔트리 ({@code HGETALL} 결과)
     * @return Gate 2, Gate 3 모두 통과하면 {@code true}
     */
    private boolean meetsBrandCorrectionGates(
            String correctedBrand,
            long correctedCount,
            Map<Object, Object> entries) {

        // __total__ 파싱
        Object totalRaw = entries.get(TOTAL_FIELD);
        if (totalRaw == null) {
            log.warn("[브랜드 핸들러][트랙2] __total__ 필드 없음. corrected='{}'", correctedBrand);
            return false;
        }
        long total;
        try {
            total = Long.parseLong(totalRaw.toString());
        } catch (NumberFormatException e) {
            log.warn("[브랜드 핸들러][트랙2] __total__ 파싱 실패. value='{}'", totalRaw);
            return false;
        }

        // Gate 2: 지지율
        if (total == 0 || (double) correctedCount / total < BRAND_CORRECTION_RATIO) {
            log.debug("[브랜드 핸들러][트랙2] Gate2 탈락. corrected='{}', ratio={}",
                    correctedBrand, total == 0 ? 0.0 : (double) correctedCount / total);
            return false;
        }

        // Gate 3: 우위 — 2위 후보 횟수 산출
        // correctedBrand 필드와 __total__ 필드를 제외한 나머지 중 최댓값
        long secondCount = entries.entrySet().stream()
                .filter(e -> !TOTAL_FIELD.equals(e.getKey().toString())
                        && !correctedBrand.equals(e.getKey().toString()))
                .mapToLong(e -> {
                    try { return Long.parseLong(e.getValue().toString()); }
                    catch (NumberFormatException ex) { return 0L; }
                })
                .max()
                .orElse(0L);

        if (secondCount > 0 && (double) correctedCount / secondCount < BRAND_CORRECTION_DOMINANCE) {
            log.debug("[브랜드 핸들러][트랙2] Gate3 탈락. corrected='{}', dominance={}",
                    correctedBrand, (double) correctedCount / secondCount);
            return false;
        }

        return true;
    }

    /**
     * 트랙 2 Redis miss 복원 — TTL 만료 후 첫 접근 시 DB에서 Hash를 재구성합니다.
     *
     * <h3>처리 순서</h3>
     * <ol>
     *   <li>{@code findBrandCorrectionCountsByOriginalBrand()}로 교체 브랜드별 횟수 조회</li>
     *   <li>DB 조회 결과가 없으면 진짜 첫 피드백 → 1 그대로 반환</li>
     *   <li>교체 브랜드별 횟수를 Hash에 HSET으로 덮어씀
     *       (이번 요청에서 HINCR로 1이 세팅된 상태를 실제 DB 값으로 교정)</li>
     *   <li>{@code countApprovedByOriginalBrandName()}으로 {@code __total__} 복원:
     *       CORRECTED 합계 + APPROVED 합계
     *       ({@code REFProductNameAliasHandler.restoreFromDbIfMiss} 이슈 2.2와 동일 패턴)</li>
     * </ol>
     *
     * @param hashKey        Redis Hash 키
     * @param originalBrand  원본 브랜드명 (DB 조회 조건)
     * @param correctedBrand 현재 요청의 교체 브랜드명 (복원 후 이 필드 값 반환용)
     * @return 복원 후 correctedBrand 필드의 실제 횟수. 진짜 첫 피드백이면 1
     */
    private long restoreBrandCorrectionFromDb(
            String hashKey, String originalBrand, String correctedBrand) {

        List<REFFeedbackBrandCorrectionCountByOriginalDto> dbRows =
                feedbackRepository.findBrandCorrectionCountsByOriginalBrand(originalBrand);

        // Case A: 진짜 첫 피드백 — DB에 이력 없음
        if (dbRows == null || dbRows.isEmpty()) return 1L;

        // Case B: TTL 만료 후 재진입 — Hash 전체 재구성
        long dbCorrectedTotal = 0L;
        long restoredCount = 1L;

        for (REFFeedbackBrandCorrectionCountByOriginalDto row : dbRows) {
            redisTemplate.opsForHash().put(
                    hashKey,
                    row.correctedBrandName(),
                    String.valueOf(row.selectionCount())
            );
            dbCorrectedTotal += row.selectionCount();
            if (correctedBrand.equals(row.correctedBrandName())) {
                restoredCount = row.selectionCount();
            }
        }

        // __total__ = CORRECTED 합계 + APPROVED 합계
        long approvedTotal = feedbackRepository.countApprovedByOriginalBrandName(originalBrand);
        long restoredTotal = dbCorrectedTotal + approvedTotal;

        redisTemplate.opsForHash().put(hashKey, TOTAL_FIELD, String.valueOf(restoredTotal));

        log.info("[브랜드 핸들러][트랙2] Redis 복원. original='{}', candidates={}, " +
                        "correctedTotal={}, approvedTotal={}, restoredTotal={}",
                originalBrand, dbRows.size(), dbCorrectedTotal, approvedTotal, restoredTotal);

        return restoredCount;
    }
}