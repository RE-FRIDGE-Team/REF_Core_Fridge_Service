package com.refridge.core_server.recognition_feedback.application;

import com.refridge.core_server.recognition_feedback.application.dto.query.REFFeedbackDetailQuery;
import com.refridge.core_server.recognition_feedback.application.dto.query.REFFeedbackListQuery;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackDetailResult;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackSummaryResult;
import com.refridge.core_server.recognition_feedback.application.mapper.REFFeedbackResultMapper;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.service.REFProductRegistrationPolicy;
import com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <h3>피드백 조회 전용 Application Service입니다.</h3>
 *
 * <p>
 * 인식 파이프라인 결과에 대한 <font color="red">사용자 피드백</font color>을 조회합니다.
 * 단건 상세 조회, 요청자별 목록 조회, 제품명 기준 집계 조회를 제공합니다.
 * </p>
 *
 * <h3>집계 조회의 역할</h3>
 * <p>
 * {@link #getAggregation(String)}은 {@code REFPositiveFeedbackAggregationHandler}의
 * Product 자동 등록 플로우에서 호출됩니다. Redis 카운터가 빠른 필터링 역할을 하고,
 * 임계값 도달 시점에 이 메서드를 통해 DB에서 정확한 집계를 수행하여
 * {@code REFProductRegistrationPolicy}의 최종 검증을 거칩니다.
 * </p>
 *
 * @author 이승훈
 * @see REFPositiveFeedbackAggregationHandler
 * @see REFProductRegistrationPolicy
 * @since 2026. 4. 4.
 */
@Service
@RequiredArgsConstructor
public class REFFeedbackQueryService {

    private final REFRecognitionFeedbackRepository feedbackRepository;
    private final REFFeedbackResultMapper feedbackResultMapper;

    @Transactional(readOnly = true)
    public REFFeedbackDetailResult getDetail(REFFeedbackDetailQuery query) {
        if (query.hasFeedbackId()) {
            return feedbackRepository.findDetailByFeedbackId(query.feedbackId())
                    .map(feedbackResultMapper::toDetailResult)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "피드백을 찾을 수 없습니다: feedbackId=" + query.feedbackId()));
        }

        if (query.hasRecognitionId()) {
            return feedbackRepository.findDetailByRecognitionId(query.recognitionId())
                    .map(feedbackResultMapper::toDetailResult)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "피드백을 찾을 수 없습니다: recognitionId=" + query.recognitionId()));
        }

        throw new IllegalArgumentException("feedbackId 또는 recognitionId 중 하나는 필수입니다.");
    }

    @Transactional(readOnly = true)
    public List<REFFeedbackSummaryResult> getListByRequester(REFFeedbackListQuery query) {
        return feedbackResultMapper.toSummaryResultList(
                feedbackRepository.findSummariesByRequesterId(
                        query.requesterId(), query.statusCode()
                )
        );
    }

    /**
     * 제품명 기준으로 피드백 집계 결과를 조회합니다.
     *
     * <h3>호출 시점</h3>
     * <p>
     * {@code REFPositiveFeedbackAggregationHandler}가 Redis 카운터({@code feedback:positive:{productName}})를
     * 통해 {@code MIN_POSITIVE} 임계값 도달을 감지한 시점에 호출됩니다.
     * Redis 카운터는 불필요한 DB 접근을 차단하는 빠른 필터링 역할만 하며,
     * 실제 등록 정책 판단({@code REFProductRegistrationPolicy.isMet()})은
     * 이 메서드의 반환값을 기반으로 수행됩니다.
     * </p>
     *
     * <h3>집계 기준</h3>
     * <ul>
     *   <li><b>승인(approvedCount)</b>: {@code status = APPROVED}인 피드백 수 — 인식 결과를 그대로 수락한 경우</li>
     *   <li><b>수정(correctedCount)</b>: {@code status = CORRECTED}인 피드백 수 — 하나 이상의 필드를 수정한 경우</li>
     * </ul>
     *
     * <h3>점수 계산 (REFProductRegistrationPolicy)</h3>
     * <pre>
     *   score = approvedCount + correctedCount × (-2)
     *   등록 조건: approvedCount >= MIN_POSITIVE(3) AND score >= SCORE_THRESHOLD(3)
     * </pre>
     *
     * <h3>빈 결과 처리</h3>
     * <p>
     * 해당 제품명에 대한 피드백이 DB에 존재하지 않으면 (approvedCount=0, correctedCount=0)인
     * 기본 결과를 반환합니다. 정책 검증에서 자연스럽게 미달 처리됩니다.
     * </p>
     *
     * @param productName 인식 파이프라인의 정제 제품명 (orig_product_name 기준)
     * @return 승인/수정 횟수를 담은 집계 결과. 피드백 없으면 (0, 0) 반환
     *
     * @author 이승훈
     * @see REFPositiveFeedbackAggregationHandler
     * @see REFProductRegistrationPolicy
     * @since 2026. 4. 4.
     */
    @Transactional(readOnly = true)
    public REFFeedbackAggregationResult getAggregation(String productName) {
        return feedbackRepository.countAggregationByProductName(productName)
                .map(feedbackResultMapper::toAggregationResult)
                .orElse(new REFFeedbackAggregationResult(productName, 0, 0));
    }
}