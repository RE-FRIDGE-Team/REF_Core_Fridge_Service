package com.refridge.core_server.recognition_feedback.application;

import com.refridge.core_server.recognition_feedback.application.dto.command.REFFeedbackApproveCommand;
import com.refridge.core_server.recognition_feedback.application.dto.command.REFFeedbackCorrectCommand;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.ar.REFRecognitionFeedback;
import com.refridge.core_server.recognition_feedback.domain.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class REFFeedbackLifeCycleService {

    private final REFRecognitionFeedbackRepository feedbackRepository;

    private static final int AUTO_APPROVE_THRESHOLD_HOURS = 72;
    private static final int AUTO_APPROVE_BATCH_SIZE = 500;

    /**
     * 인식 완료 직후 PENDING 상태의 피드백을 생성하고 저장합니다.
     * AR의 createAndSave()에 repository를 위임하여 중복 방지 + 저장까지 AR이 처리합니다.
     */
    @Transactional
    public UUID createPendingFeedback(UUID recognitionId,
                                      UUID requesterId,
                                      REFOriginalRecognitionSnapshot snapshot) {

        REFRecognitionFeedback feedback = REFRecognitionFeedback.createAndSave(
                REFRecognitionReference.of(recognitionId),
                REFRequesterReference.of(requesterId),
                snapshot,
                feedbackRepository
        );

        log.info("[Feedback 생성] feedbackId={}, recognitionId={}",
                feedback.getId().getValue(), recognitionId);

        return feedback.getId().getValue();
    }

    /**
     * 사용자가 인식 결과를 승인합니다.
     */
    @Transactional
    public void approveFeedback(REFFeedbackApproveCommand command) {
        findFeedbackById(command.feedbackId())
                .approve(command.purchasePrice());
    }

    /**
     * 사용자가 수정 폼을 제출합니다.
     * AR의 resolveWithCorrection()이 diff 계산 + approve/correct 분기까지 직접 처리합니다.
     */
    @Transactional
    public void correctFeedback(REFFeedbackCorrectCommand command) {
        findFeedbackById(command.feedbackId())
                .resolveWithCorrection(command.toCorrectionData());
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

    private REFRecognitionFeedback findFeedbackById(UUID feedbackId) {
        return feedbackRepository.findById(REFRecognitionFeedbackId.of(feedbackId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "피드백을 찾을 수 없습니다: " + feedbackId));
    }
}