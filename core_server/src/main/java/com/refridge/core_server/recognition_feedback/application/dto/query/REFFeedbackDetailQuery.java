package com.refridge.core_server.recognition_feedback.application.dto.query;

import lombok.Builder;

import java.util.UUID;

@Builder
public record REFFeedbackDetailQuery(
        UUID feedbackId,
        UUID recognitionId
) {
    /**
     * feedbackId 기준 조회
     */
    public static REFFeedbackDetailQuery byFeedbackId(UUID feedbackId) {
        return new REFFeedbackDetailQuery(feedbackId, null);
    }

    /**
     * recognitionId 기준 조회 — 클라이언트가 인식 결과 ID로 피드백을 찾을 때
     */
    public static REFFeedbackDetailQuery byRecognitionId(UUID recognitionId) {
        return new REFFeedbackDetailQuery(null, recognitionId);
    }

    public boolean hasFeedbackId() {
        return feedbackId != null;
    }

    public boolean hasRecognitionId() {
        return recognitionId != null;
    }
}