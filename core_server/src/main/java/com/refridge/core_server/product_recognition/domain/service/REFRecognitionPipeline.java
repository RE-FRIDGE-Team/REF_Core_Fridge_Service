package com.refridge.core_server.product_recognition.domain.service;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class REFRecognitionPipeline {

    private final List<REFRecognitionHandler> handlers;

    public REFRecognitionPipeline(List<REFRecognitionHandler> handlers) {
        this.handlers = handlers;
    }

    public REFRecognitionContext execute(REFRecognitionContext context) {
        for (REFRecognitionHandler handler : handlers) {
            if (context.isCompleted()) {
                log.debug("파이프라인 완료. 이후 핸들러 건너뜀. completedBy={}",
                        context.getCompletedBy());
                break;
            }

            log.debug("[{}] 핸들러 실행 시작. input='{}'",
                    handler.handlerName(), context.getEffectiveInput());

            handler.handle(context);
        }
        return context;
    }
}
