package com.refridge.core_server.product_recognition.domain.pipeline;

/**
 * Recognition 파이프라인의 각 단계를 표현하는 핸들러 인터페이스.
 * Chain of Responsibility 패턴.
 * 각 구현체는 handle() 에서:
 *  - 자신의 처리를 시도한다
 *  - 성공하면 context.complete() 를 호출하고 반환
 *  - 실패(미매칭)하면 아무것도 하지 않고 반환 → 다음 핸들러가 처리
 */
public interface REFRecognitionHandler {

    void handle(REFRecognitionContext context);

    /**
     * 핸들러 이름 (로깅용)
     */
    String handlerName();
}