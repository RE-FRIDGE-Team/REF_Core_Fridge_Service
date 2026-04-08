package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.grocery_item_correction.domain.REFGroceryItemNameCorrection;
import com.refridge.core_server.grocery_item_correction.domain.REFGroceryItemNameCorrectionRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFGroceryItemCorrectionConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 식재료명 교정 확정 서비스입니다.
 *
 * <h3>역할</h3>
 * <p>
 * {@link com.refridge.core_server.product_alias.application.REFAliasConfirmationService}가
 * 제품명 alias 교정을 담당하듯, 이 서비스는
 * "파이프라인 인식 식재료명 → 사용자 투표 교정 식재료명" 확정을 관리합니다.
 * </p>
 *
 * <h3>Redis 구조</h3>
 * <pre>
 *   Hash: feedback:grocery-item-correction:{originalName}
 *     Field: {correctedName1} → 선택 횟수
 *     Field: {correctedName2} → 선택 횟수
 *     Field: __total__        → 전체 반응 횟수 (긍정 피드백 포함)
 *     TTL: 30일 (활동 시 갱신)
 *
 *   Hash: grocery-item-correction:confirmed
 *     Field: {originalName} → {correctedName}
 *     TTL: 없음 (명시적 삭제만)
 * </pre>
 *
 * <h3>3중 게이트</h3>
 * <table border="1" cellpadding="4">
 *   <thead>
 *     <tr><th>Gate</th><th>조건</th><th>목적</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td>Gate 1</td><td>선택 횟수 >= {@link #MIN_CORRECTION_COUNT}(10)</td>
 *         <td>절대 샘플 수 보장</td></tr>
 *     <tr><td>Gate 2</td><td>선택 횟수 / __total__ >= {@link #MIN_CORRECTION_RATIO}(0.7)</td>
 *         <td>긍정 다수 시 악용 방어</td></tr>
 *     <tr><td>Gate 3</td><td>1위 / 2위 >= {@link #DOMINANCE_RATIO}(3.0)</td>
 *         <td>경쟁 후보 성급한 확정 방지</td></tr>
 *   </tbody>
 * </table>
 *
 * <h3>confirmCorrection 처리 순서</h3>
 * <ol>
 *   <li>DB 조회: 기존 {@code REFGroceryItemNameCorrection} 존재 여부 확인</li>
 *   <li>존재하면 횟수 갱신 + CANDIDATE이면 CONFIRMED 전환</li>
 *   <li>없으면 CANDIDATE로 생성 후 즉시 CONFIRMED 전환</li>
 *   <li>DB 저장</li>
 *   <li>Redis {@code grocery-item-correction:confirmed}에 즉시 반영</li>
 *   <li>{@link REFGroceryItemCorrectionConfirmedEvent} 발행</li>
 * </ol>
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 * @see com.refridge.core_server.product_alias.application.REFAliasConfirmationService
 * @see REFGroceryItemMappingHandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFGroceryItemCorrectionService {

    private final StringRedisTemplate redisTemplate;
    private final REFGroceryItemNameCorrectionRepository correctionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /* ──────────────────── 상수 ──────────────────── */

    /** Gate 1: 절대 샘플 수 보장 */
    public static final int MIN_CORRECTION_COUNT = 10;

    /** Gate 2: 전체 반응 대비 지지율 */
    public static final double MIN_CORRECTION_RATIO = 0.7;

    /** Gate 3: 1위 / 2위 최소 우위 배율 */
    public static final double DOMINANCE_RATIO = 3.0;

    /** 후보 집계 Hash 키 접두사 */
    public static final String CORRECTION_CANDIDATE_PREFIX = "feedback:grocery-item-correction:";

    /** 확정 캐시 Hash 키 */
    public static final String CORRECTION_CONFIRMED_KEY = "grocery-item-correction:confirmed";

    /** 전체 반응 횟수를 저장하는 특수 필드명 */
    public static final String TOTAL_FIELD = "__total__";

    /* ──────────────────── 게이트 검사 ──────────────────── */

    /**
     * 3중 게이트를 모두 통과하는지 검사합니다.
     *
     * <h3>Gate 3 상세</h3>
     * <p>
     * {@code allCounts}에서 현재 후보와 {@code __total__} 필드를 제외한
     * 나머지 중 최댓값을 2위로 간주합니다.
     * 2위 후보가 없거나 0이면 Gate 3은 자동 통과합니다.
     * </p>
     *
     * @param candidateKey    게이트를 검사할 교정 식재료명
     * @param occurrenceCount 이 후보의 선택 횟수
     * @param totalCount      {@code __total__} 값 (긍정 + 모든 수정본 합계)
     * @param allCounts       {@code HGETALL} 결과 — 모든 후보 및 {@code __total__} 포함
     * @return 3중 게이트를 모두 통과하면 {@code true}
     */
    public boolean meetsConfirmationThreshold(
            String candidateKey,
            long occurrenceCount,
            long totalCount,
            Map<String, Long> allCounts) {

        // Gate 1: 절대 샘플 수 미달 → 즉시 false
        if (occurrenceCount < MIN_CORRECTION_COUNT) return false;

        // Gate 2: 전체 반응 대비 지지율
        if (totalCount == 0) return false;
        double ratio = (double) occurrenceCount / totalCount;
        if (ratio < MIN_CORRECTION_RATIO) return false;

        // Gate 3: 경쟁 후보 대비 지배적 우위
        OptionalLong secondMax = allCounts.entrySet().stream()
                .filter(e -> !e.getKey().equals(candidateKey))
                .filter(e -> !e.getKey().equals(TOTAL_FIELD))
                .mapToLong(Map.Entry::getValue)
                .max();

        if (secondMax.isPresent() && secondMax.getAsLong() > 0) {
            double dominance = (double) occurrenceCount / secondMax.getAsLong();
            if (dominance < DOMINANCE_RATIO) {
                log.debug("[식재료명 교정 확정 보류] 경쟁 후보 존재. 1위={}, 2위={}, 배율={:.2f}",
                        occurrenceCount, secondMax.getAsLong(), dominance);
                return false;
            }
        }

        return true;
    }

    /**
     * Redis Hash에서 교정 후보별 선택 횟수 전체를 조회합니다.
     * {@code HGETALL} 명령으로 Hash 전체를 한 번에 읽습니다.
     *
     * @param hashKey {@code feedback:grocery-item-correction:{originalName}}
     * @return 후보명 → 선택 횟수 맵 ({@code __total__} 필드 포함)
     */
    public Map<String, Long> getAllCandidateCounts(String hashKey) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(hashKey);
        Map<String, Long> result = new HashMap<>();
        raw.forEach((k, v) -> {
            try {
                result.put(k.toString(), Long.parseLong(v.toString()));
            } catch (NumberFormatException ignored) {
                // 정상적인 상황에서는 발생하지 않아야 함
            }
        });
        return result;
    }

    /* ──────────────────── 확정 / 재심사 ──────────────────── */

    /**
     * 식재료명 교정을 확정하고 DB와 Redis에 동시에 반영합니다.
     * 확정 후 {@link REFGroceryItemCorrectionConfirmedEvent}를 발행합니다.
     *
     * <h3>처리 순서</h3>
     * <ol>
     *   <li>DB: 기존 레코드 존재 여부 확인 후 횟수 갱신 or 신규 생성</li>
     *   <li>DB: CANDIDATE → CONFIRMED 전환 후 저장</li>
     *   <li>Redis: {@code grocery-item-correction:confirmed}에 즉시 반영</li>
     *   <li>이벤트: {@link REFGroceryItemCorrectionConfirmedEvent} 발행</li>
     * </ol>
     *
     * @param originalName    파이프라인이 인식한 원본 식재료명
     * @param correctedName   확정할 교정 식재료명
     * @param occurrenceCount 이 교정본의 선택 횟수 (DB 저장용)
     * @param totalCount      전체 반응 횟수 (DB 저장용)
     */
    @Transactional
    public void confirmCorrection(String originalName, String correctedName,
                                  long occurrenceCount, long totalCount) {

        REFGroceryItemNameCorrection correction =
                correctionRepository.findByOriginalName(originalName)
                        .map(existing -> {
                            existing.updateCounts(occurrenceCount, totalCount);
                            if (!existing.isConfirmed()) existing.confirm();
                            return existing;
                        })
                        .orElseGet(() -> {
                            REFGroceryItemNameCorrection newCorrection =
                                    REFGroceryItemNameCorrection.createCandidate(
                                            originalName, correctedName,
                                            occurrenceCount, totalCount);
                            newCorrection.confirm();
                            return newCorrection;
                        });

        correctionRepository.save(correction);

        // Redis 즉시 반영 — 다음 인식 요청부터 교정 적용 가능
        redisTemplate.opsForHash().put(
                CORRECTION_CONFIRMED_KEY, originalName, correctedName);

        // Phase 4 핸들러가 GroceryItem DB 존재 여부 기반으로 후속 처리
        eventPublisher.publishEvent(new REFGroceryItemCorrectionConfirmedEvent(
                originalName, correctedName, occurrenceCount, totalCount));

        log.info("[식재료명 교정 확정] '{}' → '{}', 횟수={}/{}, 비율={:.1f}%",
                originalName, correctedName, occurrenceCount, totalCount,
                (double) occurrenceCount / totalCount * 100);
    }

    /**
     * 확정된 교정을 재심사(CANDIDATE) 상태로 되돌립니다.
     *
     * <p>
     * 경쟁 후보가 Gate 3을 위협할 수준으로 재부상했을 때 호출됩니다.
     * Redis 삭제가 먼저 반영되어 파이프라인에서 교정 적용이 즉시 중단됩니다.
     * </p>
     *
     * @param originalName reopen할 원본 식재료명
     */
    @Transactional
    public void reopenCorrection(String originalName) {
        correctionRepository.findByOriginalName(originalName)
                .filter(REFGroceryItemNameCorrection::isConfirmed)
                .ifPresent(correction -> {
                    correction.reopen();
                    correctionRepository.save(correction);
                    log.info("[식재료명 교정 재심사] '{}' CONFIRMED → CANDIDATE 전환", originalName);
                });

        // Redis에서 즉시 제거 — 다음 인식 요청부터 교정 적용 중단
        redisTemplate.opsForHash().delete(CORRECTION_CONFIRMED_KEY, originalName);
        log.info("[식재료명 교정 재심사] Redis에서 '{}' 제거", originalName);
    }

    /* ──────────────────── 조회 ──────────────────── */

    /**
     * 특정 원본 식재료명이 현재 CONFIRMED 상태인지 확인합니다.
     * Redis {@code grocery-item-correction:confirmed} Hash의 키 존재 여부로 O(1) 판단합니다.
     *
     * @param originalName 확인할 원본 식재료명
     * @return CONFIRMED이면 {@code true}
     */
    public boolean isConfirmed(String originalName) {
        return redisTemplate.opsForHash().hasKey(CORRECTION_CONFIRMED_KEY, originalName);
    }

    /**
     * 확정된 교정 식재료명을 Redis에서 O(1)으로 조회합니다.
     *
     * @param originalName 파이프라인 원본 식재료명
     * @return 확정된 교정명. CONFIRMED 상태가 아니면 {@code empty}
     */
    public Optional<String> findConfirmedCorrection(String originalName) {
        Object cached = redisTemplate.opsForHash().get(CORRECTION_CONFIRMED_KEY, originalName);
        return Optional.ofNullable(cached).map(Object::toString);
    }
}
