package com.refridge.core_server.recognition_feedback.domain.service;

import com.refridge.core_server.recognition_feedback.infra.event.improvement.REFExclusionRemovalHandler;
import org.springframework.stereotype.Service;

/**
 * <h3>비식재료 사전 키워드 자동 제거 조건을 판단하는 도메인 서비스</h3>
 *
 * <p>
 * 사용자 피드백 집계 결과를 바탕으로
 * "이 비식재료 키워드를 사전에서 자동 제거해도 안전한가"를 판단합니다.
 * {@link REFExclusionRemovalHandler}에서
 * 호출됩니다.
 * </p>
 *
 * <h3>정책: 3중 게이트</h3>
 * <pre>
 *   Gate 1: REJECTED_BUT_FOOD 피드백 수 >= MIN_REMOVAL_COUNT(5)
 *           → HGETALL 없이 빠른 차단
 *
 *   Gate 2: dispute / (accept + dispute) >= DISPUTE_RATIO_THRESHOLD(0.6)
 *           → dispute: 비식재료 반려됐지만 식재료로 정정한 피드백 수
 *           → accept:  비식재료 반려를 그냥 묵인(승인)한 피드백 수
 *
 *   Gate 3: (별도 어댑터에서 수행) correctedGroceryItemName이 GroceryItem DB에 실제 존재
 *           → 존재하지 않는 식재료명으로 정정한 경우는 신뢰할 수 없음
 * </pre>
 *
 * <h3>Gate 2 설계 의도</h3>
 * <p>
 * accept(묵인) 수가 많다는 것은 "비식재료가 맞다"고 동의한 사람이 많다는 신호입니다.
 * dispute 비율이 0.6 미만이면 실제로 비식재료일 가능성이 높으므로 자동 제거하지 않습니다.
 * </p>
 *
 * <h3>자동 제거 vs 관리자 검수</h3>
 * <pre>
 *   3중 게이트 모두 통과 → 비식재료 사전에서 자동 제거 + 검수 큐 APPROVED 처리
 *   게이트 미달           → 기존과 동일하게 관리자 수동 검수
 * </pre>
 *
 * <h3>상수 관리 원칙</h3>
 * <p>
 * {@link com.refridge.core_server.recognition_feedback.infra.event.improvement.REFExclusionRemovalHandler}의
 * Redis 카운터 빠른 차단 임계값은 이 클래스의 {@code MIN_REMOVAL_COUNT}와 동일해야 합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 6.
 * @see com.refridge.core_server.recognition_feedback.infra.event.improvement.REFExclusionRemovalHandler
 */
@Service
public class REFExclusionRemovalPolicy {

    /** Gate 1: 최소 dispute(정정) 피드백 수 */
    public static final int MIN_REMOVAL_COUNT = 5;

    /** Gate 2: dispute / (accept + dispute) 최소 비율 */
    private static final double DISPUTE_RATIO_THRESHOLD = 0.6;

    /**
     * 자동 제거 조건을 충족하는지 판단합니다.
     *
     * <p>
     * Gate 3 (GroceryItem DB 존재 여부)는 이 메서드 호출 전에 어댑터에서 검증합니다.
     * 이 메서드는 Gate 1, 2만 담당합니다.
     * </p>
     *
     * @param disputeCount 비식재료 반려 후 식재료로 정정한 피드백 수
     * @param acceptCount  비식재료 반려를 그냥 묵인(승인)한 피드백 수
     * @return 자동 제거 조건 충족 여부
     */
    public boolean isMet(long disputeCount, long acceptCount) {
        // Gate 1: 최소 dispute 수 미달이면 즉시 false
        if (disputeCount < MIN_REMOVAL_COUNT) return false;

        // Gate 2: dispute 비율 검사
        long total = disputeCount + acceptCount;
        if (total == 0) return false;

        double disputeRatio = (double) disputeCount / total;
        return disputeRatio >= DISPUTE_RATIO_THRESHOLD;
    }

    /**
     * dispute 비율을 계산합니다. (로깅/진단용)
     *
     * @param disputeCount 정정 피드백 수
     * @param acceptCount  묵인 피드백 수
     * @return dispute 비율 (0.0 ~ 1.0)
     */
    public double calculateDisputeRatio(long disputeCount, long acceptCount) {
        long total = disputeCount + acceptCount;
        return total == 0 ? 0.0 : (double) disputeCount / total;
    }
}