package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * GroceryItem 매핑 확정 서비스입니다.
 *
 * <h3>역할</h3>
 * <p>
 * {@link REFAliasConfirmationService}가 제품명 alias를 관리하듯,
 * 이 서비스는 "파이프라인이 인식한 groceryItemName → 사용자가 수정한 correctedGroceryItemName"
 * 매핑의 확정을 관리합니다.
 * </p>
 *
 * <h3>Redis 구조</h3>
 * <pre>
 *   Hash: feedback:grocery-item-mapping:{originalGroceryItemName}
 *     Field: {correctedGroceryItemName1} → 선택 횟수
 *     Field: {correctedGroceryItemName2} → 선택 횟수
 *     Field: __total__                   → 전체 반응 횟수 (긍정 포함)
 *     TTL: 30일
 *
 *   Hash: grocery-item-mapping:confirmed
 *     Field: {originalGroceryItemName} → {correctedGroceryItemName}
 *     TTL: 없음
 * </pre>
 *
 * <h3>3중 게이트</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th>Gate</th><th>조건</th><th>목적</th></tr>
 *   <tr><td>Gate 1</td><td>선택 횟수 >= MIN_MAPPING_COUNT(10)</td><td>소수 피드백 확정 방지</td></tr>
 *   <tr><td>Gate 2</td><td>선택 횟수 / __total__ >= 0.7</td><td>긍정 다수 시 악용 방어</td></tr>
 *   <tr><td>Gate 3</td><td>1위 / 2위 >= 3.0</td><td>경쟁 후보 재부상 방지</td></tr>
 * </table>
 *
 * @author 이승훈
 * @since 2026. 4. 7.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFGroceryItemMappingConfirmationService {

    private final StringRedisTemplate redisTemplate;

    public static final int MIN_MAPPING_COUNT = 10;
    public static final double MIN_MAPPING_RATIO = 0.7;
    public static final double DOMINANCE_RATIO = 3.0;

    public static final String MAPPING_CANDIDATE_PREFIX = "feedback:grocery-item-mapping:";
    public static final String MAPPING_CONFIRMED_KEY = "grocery-item-mapping:confirmed";
    public static final String TOTAL_FIELD = "__total__";

    /**
     * 3중 게이트를 모두 통과하는지 검사합니다.
     * REFAliasConfirmationService.meetsConfirmationThreshold()와 동일한 구조.
     */
    public boolean meetsConfirmationThreshold(
            String candidateKey,
            long occurrenceCount,
            long totalCount,
            Map<String, Long> allCounts) {

        if (occurrenceCount < MIN_MAPPING_COUNT) return false;

        if (totalCount == 0) return false;
        double ratio = (double) occurrenceCount / totalCount;
        if (ratio < MIN_MAPPING_RATIO) return false;

        OptionalLong secondMax = allCounts.entrySet().stream()
                .filter(e -> !e.getKey().equals(candidateKey))
                .filter(e -> !e.getKey().equals(TOTAL_FIELD))
                .mapToLong(Map.Entry::getValue)
                .max();

        if (secondMax.isPresent() && secondMax.getAsLong() > 0) {
            double dominance = (double) occurrenceCount / secondMax.getAsLong();
            if (dominance < DOMINANCE_RATIO) {
                log.debug("[GroceryItem 매핑 확정 보류] 경쟁 후보 존재. 1위={}, 2위={}, 배율={:.2f}",
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
            } catch (NumberFormatException ignored) {
            }
        });
        return result;
    }

    /**
     * GroceryItem 매핑을 확정하고 Redis에 반영합니다.
     * Phase 2에서 DB 저장 로직이 추가됩니다.
     */
    public void confirmMapping(String originalName, String correctedName) {
        redisTemplate.opsForHash().put(MAPPING_CONFIRMED_KEY, originalName, correctedName);
        log.info("[GroceryItem 매핑 확정] '{}' → '{}'", originalName, correctedName);
    }

    /**
     * 확정된 매핑을 재심사(reopen) 상태로 되돌립니다.
     */
    public void reopenMapping(String originalName) {
        redisTemplate.opsForHash().delete(MAPPING_CONFIRMED_KEY, originalName);
        log.info("[GroceryItem 매핑 재심사] '{}' CONFIRMED → CANDIDATE 전환", originalName);
    }

    /**
     * 특정 originalName이 현재 CONFIRMED 상태인지 확인합니다.
     */
    public boolean isConfirmed(String originalName) {
        return redisTemplate.opsForHash().hasKey(MAPPING_CONFIRMED_KEY, originalName);
    }

    /**
     * 확정된 매핑을 Redis에서 O(1)으로 조회합니다.
     */
    public Optional<String> findConfirmedMapping(String originalName) {
        Object cached = redisTemplate.opsForHash().get(MAPPING_CONFIRMED_KEY, originalName);
        return Optional.ofNullable(cached).map(Object::toString);
    }
}