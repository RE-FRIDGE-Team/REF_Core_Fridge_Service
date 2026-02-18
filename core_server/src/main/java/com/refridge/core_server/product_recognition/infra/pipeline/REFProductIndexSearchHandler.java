package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFProductIndexSearcher;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductIndexSearchHandler implements REFRecognitionHandler {

    // TODO : Product 도메인과 연동할 수 있는 포트 개발. 포트 -> 도메인 / 포트 구현체 -> 인프라
    private final REFProductIndexSearcher productIndexSearcher;

    @Override
    public void handle(REFRecognitionContext context) {
        String inputText = context.getEffectiveInput();

        productIndexSearcher.search(inputText)
                .ifPresent(searchInfo -> {
                    REFProductRecognitionOutput output = REFProductRecognitionOutput.of(
                            searchInfo.groceryItemId(),
                            searchInfo.groceryItemName(),
                            searchInfo.categoryPath(),
                            searchInfo.imageUrl()
                    );
                    context.getRecognition().completeWithProductIndexMatch(output);
                    context.complete(output, handlerName());

                    log.info("제품 색인 매칭 성공. input='{}', matched='{}'",
                            inputText, searchInfo.groceryItemName());
                });
    }

    @Override
    public String handlerName() {
        return "ProductIndexSearch";
    }
}
