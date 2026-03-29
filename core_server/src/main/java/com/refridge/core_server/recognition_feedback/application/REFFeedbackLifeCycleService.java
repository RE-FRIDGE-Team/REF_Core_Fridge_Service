package com.refridge.core_server.recognition_feedback.application;

import com.refridge.core_server.recognition_feedback.application.dto.command.REFFeedbackApproveCommand;
import com.refridge.core_server.recognition_feedback.application.dto.command.REFFeedbackCorrectCommand;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.ar.REFRecognitionFeedback;
import com.refridge.core_server.recognition_feedback.domain.port.REFRecognitionResultQueryPort;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class REFFeedbackLifeCycleService {

    private final REFRecognitionFeedbackRepository feedbackRepository;
    private final REFRecognitionResultQueryPort recognitionQueryPort;

    private static final int AUTO_APPROVE_THRESHOLD_HOURS = 72;
    private static final int AUTO_APPROVE_BATCH_SIZE = 500;

    /**
     * 사용자가 인식 결과를 승인합니다.
     * AR의 findOrCreate()로 피드백 존재를 보장한 뒤 approve()를 호출합니다.
     */
    @Transactional
    public void approveFeedback(REFFeedbackApproveCommand command) {
        REFRecognitionFeedback.findOrCreate(
                command.recognitionId(), feedbackRepository, recognitionQueryPort
        ).approve(command.purchasePrice());
    }

    /**
     * 사용자가 수정 폼을 제출합니다.
     * AR의 findOrCreate()로 피드백 존재를 보장한 뒤 resolveWithCorrection()을 호출합니다.
     */
    @Transactional
    public void correctFeedback(REFFeedbackCorrectCommand command) {
        REFRecognitionFeedback.findOrCreate(
                command.recognitionId(), feedbackRepository, recognitionQueryPort
        ).resolveWithCorrection(command.toCorrectionData());
    }

    /**
     * 미응답 PENDING 피드백을 자동 승인 처리합니다 (배치).
     *
     * @return 자동 승인 처리된 건수
     */
    @Transactional
    public int autoApprovePendingFeedbacks() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(AUTO_APPROVE_THRESHOLD_HOURS);

        List<REFRecognitionFeedbackId> pendingIds = feedbackRepository
                .findPendingIdsCreatedBefore(threshold, AUTO_APPROVE_BATCH_SIZE);

        if (pendingIds.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (REFRecognitionFeedbackId feedbackId : pendingIds) {
            try {
                feedbackRepository.findById(feedbackId)
                        .ifPresent(REFRecognitionFeedback::autoApprove);
                processed++;
            } catch (Exception e) {
                log.error("[Feedback 자동승인] 처리 실패. feedbackId={}, 사유: {}",
                        feedbackId.getValue(), e.getMessage());
            }
        }

        log.info("[Feedback 자동승인] 대상: {}건, 처리: {}건", pendingIds.size(), processed);
        return processed;
    }
}