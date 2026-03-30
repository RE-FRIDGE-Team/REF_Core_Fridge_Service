package com.refridge.core_server.recognition_feedback.infra.adapter;

import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.port.REFCorrectionHistoryQueryPort;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackCorrectionHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link REFCorrectionHistoryQueryPort}의 구현체.
 * <p>
 * 피드백 Repository의 QueryDSL 구현에 위임합니다.
 */
@Component
@RequiredArgsConstructor
public class REFCorrectionHistoryQueryAdapter implements REFCorrectionHistoryQueryPort {

    private final REFRecognitionFeedbackRepository feedbackRepository;

    @Override
    public List<REFFeedbackCorrectionHistoryDto> findByProductName(String originalProductName, int limit) {
        return feedbackRepository.findCorrectionHistoryByProductName(originalProductName, limit);
    }
}