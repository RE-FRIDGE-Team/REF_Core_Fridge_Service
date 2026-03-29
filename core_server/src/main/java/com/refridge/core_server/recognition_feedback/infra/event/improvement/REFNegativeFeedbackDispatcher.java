package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 부정 피드백 이벤트를 구독하고, diff에 포함된 각 변경 유형에 대해
 * 해당하는 {@link REFImprovementActionHandler}에 위임하는 디스패처입니다.
 * <p>
 * 하나의 피드백에서 여러 필드가 동시에 변경된 경우 (e.g. 브랜드 + 식재료 동시 변경),
 * 각 유형에 대응하는 핸들러가 독립적으로 실행됩니다.
 */
@Slf4j
@Component
public class REFNegativeFeedbackDispatcher {

    private final Map<REFCorrectionType, REFImprovementActionHandler> handlerMap;

    public REFNegativeFeedbackDispatcher(List<REFImprovementActionHandler> handlers) {
        this.handlerMap = new EnumMap<>(REFCorrectionType.class);
        handlers.forEach(h -> this.handlerMap.put(h.supportedType(), h));

        log.info("[FeedbackDispatcher] 등록된 개선 핸들러: {}개 — {}",
                handlerMap.size(), handlerMap.keySet());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(REFNegativeFeedbackEvent event) {
        Set<REFCorrectionType> changedFields = event.diff().getChangedFields();

        if (changedFields.isEmpty()) {
            log.debug("[FeedbackDispatcher] 변경 필드 없음. feedbackId={}", event.feedbackId().getValue());
            return;
        }

        log.info("[FeedbackDispatcher] 부정 피드백 처리 시작. feedbackId={}, changes={}",
                event.feedbackId().getValue(), changedFields);

        for (REFCorrectionType type : changedFields) {
            REFImprovementActionHandler handler = handlerMap.get(type);

            if (handler == null) {
                log.debug("[FeedbackDispatcher] 핸들러 미등록 유형 스킵: {}", type);
                continue;
            }

            try {
                handler.handle(event);
                log.info("[FeedbackDispatcher] 개선 액션 완료. type={}, feedbackId={}",
                        type, event.feedbackId().getValue());
            } catch (Exception e) {
                // 하나의 핸들러 실패가 나머지에 영향 주지 않음
                log.error("[FeedbackDispatcher] 개선 액션 실패. type={}, feedbackId={}, 사유: {}",
                        type, event.feedbackId().getValue(), e.getMessage());
            }
        }
    }
}