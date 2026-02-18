package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFMLPredictionClient;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFMLPredictionHandler implements REFRecognitionHandler {

    // TODO : MLPredictionClient 구현체 개발 필요. 포트 -> 도메인 / 포트 구현체 -> 인프라
    private final REFMLPredictionClient mlPredictionClient;

    @Override
    public void handle(REFRecognitionContext context) {
        String inputText = context.getEffectiveInput();

        mlPredictionClient.predict(inputText)
                .ifPresentOrElse(
                        prediction -> {
                            // TODO: GroceryItem 조회 포트 연동 필요
                            REFProductRecognitionOutput output = REFProductRecognitionOutput.of(
                                    0L, // TODO: 실제 ID로 교체
                                    prediction.predictedGroceryItemName(),
                                    "UNKNOWN", // TODO: 실제 카테고리로 교체
                                    null
                            );
                            context.getRecognition().completeWithMLPrediction(output);
                            context.complete(output, handlerName());

                            log.info("ML 예측 성공. input='{}', predicted='{}', confidence={}",
                                    inputText,
                                    prediction.predictedGroceryItemName(),
                                    prediction.confidence());
                        },
                        () -> {
                            // ML도 실패 → 최종 NO_MATCH
                            context.getRecognition().failToMatch();
                            context.reject(handlerName() + ":NO_MATCH");
                            log.info("ML 예측 실패. NO_MATCH 처리. input='{}'", inputText);
                        }
                );
    }

    @Override
    public String handlerName() {
        return "MLPrediction";
    }
}
