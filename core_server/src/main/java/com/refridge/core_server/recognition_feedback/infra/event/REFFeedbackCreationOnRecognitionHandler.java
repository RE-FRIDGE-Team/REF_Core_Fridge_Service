package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService;
import com.refridge.core_server.product_recognition.domain.event.REFRecognitionCompletedEvent;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.ar.REFRecognitionFeedback;
import com.refridge.core_server.recognition_feedback.domain.port.REFRecognitionResultQueryPort;
import com.refridge.core_server.recognition_feedback.domain.vo.REFFeedbackStatus;
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
 * <h3>인식 완료 기반 피드백 자동 생성 핸들러</h3>
 *
 * <p>
 * 인식 도메인(Recognition BC)의 완료 이벤트를 구독하여 피드백 도메인(Feedback BC)의
 * Aggregate Root(AR)를 생성하는 역할을 수행합니다.
 * </p>
 *
 * <h4>1. 설계 원칙 (Separation of Concerns)</h4>
 * <ul>
 * <li><b>디커플링:</b> 피드백 BC에 위치하며, 인식 BC의 코드를 전혀 수정하지 않습니다.</li>
 * <li><b>안정성:</b> {@code AFTER_COMMIT} 단계에서 실행되어, 피드백 생성 실패가 원본 인식 트랜잭션의 롤백을 유발하지 않습니다.</li>
 * </ul>
 *
 * <h4>2. 비즈니스 로직 파이프라인</h4>
 * <ol>
 * <li>인식 엔진({@link REFProductRecognitionAppService#recognize})의 5단계 파이프라인 완료</li>
 * <li>{@link REFRecognitionCompletedEvent} 이벤트 발행</li>
 * <li>본 핸들러가 이벤트를 구독하여 {@link REFRecognitionFeedback} AR 생성</li>
 * <li>상태값 {@link REFFeedbackStatus#PENDING}으로 초기화</li>
 * </ol>
 *
 * <h4>3. 자가 치유 및 보상 메커니즘 (Self-Healing)</h4>
 * <p>
 * 비동기 처리나 핸들러 오류로 인해 피드백 생성에 실패하더라도, 이후 <b>Approve(승인)</b> 또는
 * <b>Correct(교정)</b> 시점에 실행되는 <b>Lazy Creation(findOrCreate)</b> 로직에 의해
 * 누락 없는 데이터 생성을 보장합니다.
 * </p>
 *
 * @author  이승훈
 * @since 2026. 4. 3.
 * @see REFProductRecognitionAppService#recognize
 * @see REFRecognitionCompletedEvent
 * @see REFRecognitionFeedback
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