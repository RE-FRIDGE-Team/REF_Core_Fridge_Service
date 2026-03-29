package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.product_recognition.domain.event.REFRecognitionCompletedEvent;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.ar.REFRecognitionFeedback;
import com.refridge.core_server.recognition_feedback.domain.port.REFRecognitionResultQueryPort;
import com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionReference;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRequesterReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * 인식 완료 이벤트를 구독하여 피드백 AR을 생성하는 핸들러입니다.
 * <p>
 * 피드백 BC에 위치하여, 인식 BC의 코드를 전혀 수정하지 않습니다.
 * <p>
 * {@code AFTER_COMMIT}: 인식 트랜잭션 커밋 이후 별도 트랜잭션에서 실행.
 * 피드백 생성 실패가 인식 롤백을 유발하지 않습니다.
 * <p>
 * 이 핸들러가 실패하더라도, approve/correct 시점의 Lazy Creation({@code findOrCreate})이
 * 보상하므로 피드백이 누락되지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFFeedbackCreationOnRecognitionHandler {

    private final REFRecognitionFeedbackRepository feedbackRepository;
    private final REFRecognitionResultQueryPort recognitionQueryPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(REFRecognitionCompletedEvent event) {
        UUID recognitionId = event.recognitionId();

        try {
            REFOriginalRecognitionSnapshot snapshot = recognitionQueryPort
                    .findSnapshotByRecognitionId(recognitionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "인식 결과 조회 실패: " + recognitionId));

            REFRecognitionFeedback.createAndSave(
                    REFRecognitionReference.of(recognitionId),
                    REFRequesterReference.fromString(event.requesterId()),
                    snapshot,
                    feedbackRepository
            );

            log.info("[Feedback 이벤트] 피드백 생성 완료. recognitionId={}", recognitionId);

        } catch (Exception e) {
            // 실패해도 인식에 영향 없음. Lazy Creation이 보상.
            log.warn("[Feedback 이벤트] 피드백 생성 실패 — Lazy Creation으로 보상 예정. recognitionId={}, 사유: {}",
                    recognitionId, e.getMessage());
        }
    }
}