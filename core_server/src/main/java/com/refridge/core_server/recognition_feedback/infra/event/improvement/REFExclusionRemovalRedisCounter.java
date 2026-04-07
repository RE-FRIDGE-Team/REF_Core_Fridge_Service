package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 비식재료 반려 관련 사용자 반응을 Redis에 집계하는 컴포넌트입니다.
 *
 * <h3>Redis 키 구조</h3>
 * <pre>
 *   String: feedback:exclusion-dispute:{rejectionKeyword}
 *           → 비식재료 반려됐으나 식재료로 정정(부정 피드백)한 횟수
 *
 *   String: feedback:exclusion-accept:{rejectionKeyword}
 *           → 비식재료 반려를 그냥 묵인(긍정 피드백)한 횟수
 * </pre>
 *
 * <h3>TTL 정책</h3>
 * <p>
 * 30일간 해당 키워드에 대한 피드백이 없으면 자동 만료됩니다.
 * 카운터가 만료되어 리셋되더라도, 게이트 조건 재충족 시 자동 제거가 다시 트리거됩니다.
 * 비식재료 사전 제거는 멱등하므로 중복 실행되어도 안전합니다.
 * </p>
 *
 * <h3>호출 지점</h3>
 * <ul>
 *   <li>{@code dispute} 증가:
 *       {@link REFExclusionRemovalHandler} — 부정 피드백 시</li>
 *   <li>{@code accept} 증가:
 *       {@link REFPositiveFeedbackAggregationHandler}
 *       — 긍정 피드백 중 rejected=true 케이스</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 4. 6.
 * @see REFExclusionRemovalHandler
 * @see com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFExclusionRemovalRedisCounter {

    private final StringRedisTemplate redisTemplate;

    public static final String DISPUTE_PREFIX = "feedback:exclusion-dispute:";
    public static final String ACCEPT_PREFIX  = "feedback:exclusion-accept:";

    /** 카운터 TTL — 30일간 활동 없으면 자동 만료 */
    private static final Duration COUNTER_TTL = Duration.ofDays(30);

    /**
     * dispute 카운터 (식재료로 정정)를 1 증가시키고 현재 값을 반환합니다.
     *
     * @param rejectionKeyword 비식재료 사전에서 매칭된 키워드
     * @return 증가 후 dispute 카운트. Redis 장애 시 {@code null}
     */
    public Long incrementDispute(String rejectionKeyword) {
        String key = DISPUTE_PREFIX + rejectionKeyword;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null) {
            redisTemplate.expire(key, COUNTER_TTL);
        }
        return count;
    }

    /**
     * accept 카운터 (비식재료 반려 묵인)를 1 증가시킵니다.
     *
     * @param rejectionKeyword 비식재료 사전에서 매칭된 키워드
     */
    public void incrementAccept(String rejectionKeyword) {
        String key = ACCEPT_PREFIX + rejectionKeyword;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null) {
            redisTemplate.expire(key, COUNTER_TTL);
        }
        log.debug("[ExclusionCounter] accept +1. keyword='{}', count={}", rejectionKeyword, count);
    }

    /**
     * dispute 카운터 값을 조회합니다.
     *
     * @param rejectionKeyword 비식재료 사전에서 매칭된 키워드
     * @return 현재 dispute 카운트. 키 없으면 0
     */
    public long getDisputeCount(String rejectionKeyword) {
        String value = redisTemplate.opsForValue().get(DISPUTE_PREFIX + rejectionKeyword);
        return parseLong(value);
    }

    /**
     * accept 카운터 값을 조회합니다.
     *
     * @param rejectionKeyword 비식재료 사전에서 매칭된 키워드
     * @return 현재 accept 카운트. 키 없으면 0
     */
    public long getAcceptCount(String rejectionKeyword) {
        String value = redisTemplate.opsForValue().get(ACCEPT_PREFIX + rejectionKeyword);
        return parseLong(value);
    }

    /**
     * 두 카운터를 모두 삭제합니다.
     * 자동 제거 완료 후 카운터를 초기화할 때 호출합니다.
     *
     * @param rejectionKeyword 비식재료 사전에서 매칭된 키워드
     */
    public void resetCounters(String rejectionKeyword) {
        redisTemplate.delete(DISPUTE_PREFIX + rejectionKeyword);
        redisTemplate.delete(ACCEPT_PREFIX + rejectionKeyword);
        log.info("[ExclusionCounter] 카운터 초기화. keyword='{}'", rejectionKeyword);
    }

    private long parseLong(String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}