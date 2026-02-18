package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFExclusionWordMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFExclusionFilterHandler implements REFRecognitionHandler {

    private final REFExclusionWordMatcher exclusionWordMatcher;

    @Override
    public void handle(REFRecognitionContext context) {
        exclusionWordMatcher.findMatch(context.getRawInput())
                .ifPresent(matched -> {
                    log.info("비식재료 필터 매칭. input='{}', matched='{}'",
                            context.getRawInput(), matched);
                    context.getRecognition().rejectAsNonFood();
                    context.reject(handlerName());
                });
    }

    @Override
    public String handlerName() {
        return "ExclusionFilter";
    }

}
