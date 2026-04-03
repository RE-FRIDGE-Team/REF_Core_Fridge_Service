package com.refridge.core_server.product_alias.application;

import com.refridge.core_server.product_alias.domain.REFProductNameAlias;
import com.refridge.core_server.product_alias.domain.REFProductNameAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * alias 확정/재심사 도메인 서비스입니다.
 *
 * <h3>확정 조건 — 3중 게이트</h3>
 * <pre>
 *   Gate 1: 1위 선택 횟수 >= MIN_ALIAS_COUNT (10)
 *   Gate 2: 1위 선택 비율 >= MIN_ALIAS_RATIO (0.7)
 *            분모 = __total__ (긍정 피드백 + 모든 수정본 선택 합계)
 *   Gate 3: 2위가 없거나 (1위 / 2위) >= DOMINANCE_RATIO (3.0)
 * </pre>
 *
 * <h3>Gate 2 분모에 긍정 피드백 포함</h3>
 * __total__은 REFPositiveFeedbackAggregationHandler에서 긍정 피드백 발생 시에도 +1 됩니다.
 * 이로써 파이프라인 인식 결과를 1000명이 승인했는데
 * 수정본 20명으로 alias가 확정되는 오류를 방지합니다.
 *
 * <h3>Redis 구조</h3>
 * <pre>
 *   Hash: feedback:product-alias:{originalName}   ← AliasHandler/PositiveHandler 관리
 *     Field: {correctedName} → 수정본 선택 횟수
 *     Field: __total__       → 전체 선택 횟수 (긍정 승인 포함)
 *
 *   Hash: alias:confirmed                          ← 이 서비스 관리
 *     Field: {originalName} → {aliasName}
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFAliasConfirmationService {

    private final REFProductNameAliasRepository aliasRepository;
    private final StringRedisTemplate redisTemplate;

    public static final int    MIN_ALIAS_COUNT  = 10;
    public static final double MIN_ALIAS_RATIO  = 0.7;
    public static final double DOMINANCE_RATIO  = 3.0;

    public static final String ALIAS_CONFIRMED_KEY = "alias:confirmed";
    public static final String TOTAL_FIELD         = "__total__";

    /**
     * 3중 게이트를 모두 통과하는지 검사합니다.
     */
    public boolean meetsConfirmationThreshold(
            String candidateKey,
            long occurrenceCount,
            long totalCount,
            Map<String, Long> allCounts) {

        // Gate 1: 절대 횟수
        if (occurrenceCount < MIN_ALIAS_COUNT) return false;

        // Gate 2: 비율 (분모 = __total__ → 긍정 피드백 포함)
        if (totalCount == 0) return false;
        double ratio = (double) occurrenceCount / totalCount;
        if (ratio < MIN_ALIAS_RATIO) return false;

        // Gate 3: 지배적 우세 — 2위와의 격차
        OptionalLong secondMax = allCounts.entrySet().stream()
                .filter(e -> !e.getKey().equals(candidateKey))
                .filter(e -> !e.getKey().equals(TOTAL_FIELD))
                .mapToLong(Map.Entry::getValue)
                .max();

        if (secondMax.isPresent() && secondMax.getAsLong() > 0) {
            double dominance = (double) occurrenceCount / secondMax.getAsLong();
            if (dominance < DOMINANCE_RATIO) {
                log.debug("[Alias 확정 보류] 경쟁 후보 존재. 1위={}, 2위={}, 배율={:.2f}",
                        occurrenceCount, secondMax.getAsLong(), dominance);
                return false;
            }
        }

        return true;
    }

    /**
     * Redis Hash에서 후보별 선택 횟수 전체를 조회합니다.
     */
    public Map<String, Long> getAllCandidateCounts(String hashKey) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(hashKey);
        Map<String, Long> result = new HashMap<>();
        raw.forEach((k, v) -> {
            try {
                result.put(k.toString(), Long.parseLong(v.toString()));
            } catch (NumberFormatException ignored) {}
        });
        return result;
    }

    /**
     * alias 확정 — DB CONFIRMED 저장 + Redis alias:confirmed 캐싱.
     */
    @Transactional
    public void confirmAlias(String originalName, String aliasName,
                             long occurrenceCount, long totalCount) {
        REFProductNameAlias alias = aliasRepository.findByOriginalName(originalName)
                .map(existing -> {
                    existing.updateCounts(occurrenceCount, totalCount);
                    if (!existing.isConfirmed()) existing.confirm();
                    return existing;
                })
                .orElseGet(() -> {
                    REFProductNameAlias newAlias = REFProductNameAlias.createCandidate(
                            originalName, aliasName, occurrenceCount, totalCount);
                    newAlias.confirm();
                    return newAlias;
                });

        aliasRepository.save(alias);
        redisTemplate.opsForHash().put(ALIAS_CONFIRMED_KEY, originalName, aliasName);

        log.info("[Alias 확정] '{}' → '{}', 횟수={}/{}, 비율={:.1f}%",
                originalName, aliasName, occurrenceCount, totalCount,
                (double) occurrenceCount / totalCount * 100);
    }

    /**
     * alias 재심사 — DB CANDIDATE 전환 + Redis alias:confirmed 에서 제거.
     *
     * reopen 이후에는:
     *   - 파싱 파이프라인: alias 교체 없이 원본명으로 탐색 (정상)
     *   - AppService: alias:confirmed HGET → miss → alias 미적용
     *   - correctionSuggestions: 다시 노출됨
     *   - Product 등록: 다음 임계값 도달 시 재시도
     */
    @Transactional
    public void reopenAlias(String originalName) {
        aliasRepository.findByOriginalName(originalName)
                .filter(REFProductNameAlias::isConfirmed)
                .ifPresent(alias -> {
                    alias.reopen();
                    aliasRepository.save(alias);
                    log.info("[Alias 재심사] '{}' CONFIRMED → CANDIDATE 전환", originalName);
                });

        // Redis alias:confirmed에서 제거 — 즉시 응답에서 alias 교체 중단
        redisTemplate.opsForHash().delete(ALIAS_CONFIRMED_KEY, originalName);
        log.info("[Alias 재심사] Redis alias:confirmed 에서 '{}' 제거", originalName);
    }

    /**
     * 특정 원본 제품명이 CONFIRMED 상태인지 확인합니다.
     * REFProductNameAliasHandler에서 reopen 판단에 사용합니다.
     */
    public boolean isConfirmed(String originalName) {
        return redisTemplate.opsForHash().hasKey(ALIAS_CONFIRMED_KEY, originalName);
    }

    /**
     * CONFIRMED alias를 Redis에서 O(1)으로 조회합니다.
     * AppService에서 응답 수준 alias 교체에 사용합니다.
     */
    public Optional<String> findConfirmedAlias(String originalName) {
        Object cached = redisTemplate.opsForHash().get(ALIAS_CONFIRMED_KEY, originalName);
        return Optional.ofNullable(cached).map(Object::toString);
    }
}