package com.refridge.core_server.recognition_feedback.application;

import com.refridge.core_server.recognition_feedback.application.dto.query.REFFeedbackDetailQuery;
import com.refridge.core_server.recognition_feedback.application.dto.query.REFFeedbackListQuery;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackDetailResult;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackSummaryResult;
import com.refridge.core_server.recognition_feedback.application.mapper.REFFeedbackResultMapper;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public REFFeedbackAggregationResult getAggregation(String productName) {
        return feedbackRepository.countAggregationByProductName(productName)
                .map(feedbackResultMapper::toAggregationResult)
                .orElse(new REFFeedbackAggregationResult(productName, 0, 0));
    }
}