package com.refridge.core_server.recognition_feedback.domain.service;

import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler;
import org.springframework.stereotype.Service;

/**
 * <h3>Product 자동 등록 조건을 판단하는 도메인 서비스</h3>
 * <p>
 * 피드백 집계 결과를 바탕으로 "이 제품을 Product로 등록할 만큼 충분히 검증됐는가"를
 * 결정하는 비즈니스 정책을 단일 책임으로 관리합니다.<br>
 *
 * {@link REFPositiveFeedbackAggregationHandler}에서 긍정 피드백 검사 후 정책에 의해 임계값 검사를 할 때 사용합니다.
 *
 * <h3>정책: 가중 점수제 + 최소 긍정수 하한</h3>
 * <pre>
 *   score = 긍정수 * POSITIVE_WEIGHT(+1) + 부정수 * NEGATIVE_WEIGHT(-2)
 *   등록 조건: 긍정수 >= MIN_POSITIVE(3) AND score >= SCORE_THRESHOLD(3)
 * </pre>
 *
 * 케이스별 동작:
 * <pre>
 *   긍정 3 / 부정 0 → score= 3 → MIN_POSITIVE ✅, score ✅ → 등록
 *   긍정 5 / 부정 0 → score= 5 → MIN_POSITIVE ✅, score ✅ → 등록
 *   긍정 5 / 부정 2 → score= 1 → MIN_POSITIVE ✅, score ❌ → 미등록
 *   긍정 7 / 부정 1 → score= 5 → MIN_POSITIVE ✅, score ✅ → 등록
 *   긍정 3 / 부정 2 → score=-1 → MIN_POSITIVE ✅, score ❌ → 미등록
 *   긍정 20/ 부정 3 → score=14 → MIN_POSITIVE ✅, score ✅ → 등록
 * </pre>
 *
 * <h3>설계 의도</h3>
 * <ul>
 *   <li>긍정만 받는 제품: 3번째부터 등록 (빠른 파이프라인 강화)</li>
 *   <li>부정이 섞인 제품: 부정 1건당 긍정 2건이 더 필요 (신중한 등록)</li>
 *   <li>MIN_POSITIVE 하한: 긍정 1~2건만으로 등록되는 것을 방지</li>
 * </ul>
 *
 * <h3>상수 관리 원칙</h3>
 * 등록 정책 상수는 이 클래스에서만 관리합니다.
 * {@link com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler}의
 * {@code COUNTER_QUICK_FILTER}는 이 클래스의 {@code MIN_POSITIVE}와 동일한 값이어야 합니다.
 * 두 값이 어긋나면 Redis 빠른 차단이 의미를 잃습니다.
 */
@Service
public class REFProductRegistrationPolicy {

    /** 최소 긍정 하한 — 긍정 1~2건만으로 등록되는 것을 방지 */
    public static final int MIN_POSITIVE    = 3;

    /** 등록을 위한 최소 가중 점수 */
    private static final int SCORE_THRESHOLD = 3;

    /** 긍정 피드백 1건당 점수 */
    private static final int POSITIVE_WEIGHT = 1;

    /** 부정 피드백 1건당 점수 (패널티) */
    private static final int NEGATIVE_WEIGHT = -2;

    /**
     * 피드백 집계 결과가 Product 자동 등록 조건을 충족하는지 판단합니다.
     *
     * @param aggregation 피드백 집계 결과
     * @return 등록 조건 충족 여부
     */
    public boolean isMet(REFFeedbackAggregationResult aggregation) {
        long positive = aggregation.approvedCount();
        long negative = aggregation.correctedCount();

        int score = (int) (positive * POSITIVE_WEIGHT + negative * NEGATIVE_WEIGHT);

        return positive >= MIN_POSITIVE && score >= SCORE_THRESHOLD;
    }

    /**
     * 가중 점수를 계산합니다.
     * 핸들러에서 로깅 목적으로 사용합니다.
     *
     * @param aggregation 피드백 집계 결과
     * @return 가중 점수
     */
    public int calculateScore(REFFeedbackAggregationResult aggregation) {
        return (int) (aggregation.approvedCount() * POSITIVE_WEIGHT
                + aggregation.correctedCount() * NEGATIVE_WEIGHT);
    }
}