package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;

/**
 * 부정 피드백에 의해 트리거되는 개선 액션의 Strategy 인터페이스입니다.
 * <p>
 * 각 구현체는 특정 {@link REFCorrectionType}에 대해 개선 액션을 수행합니다.
 * {@link REFNegativeFeedbackDispatcher}가 diff의 변경 필드를 순회하며
 * 해당 유형을 지원하는 핸들러에 위임합니다.
 */
public interface REFImprovementActionHandler {

    /** 이 핸들러가 처리할 수 있는 수정 유형 */
    REFCorrectionType supportedType();

    /** 개선 액션 실행 */
    void handle(REFNegativeFeedbackEvent event);
}