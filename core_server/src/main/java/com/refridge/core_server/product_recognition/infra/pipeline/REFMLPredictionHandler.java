package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFGroceryItemQueryClient;
import com.refridge.core_server.product_recognition.domain.port.REFMLPredictionClient;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFMLPredictionHandler implements REFRecognitionHandler {

    private final REFMLPredictionClient mlPredictionClient;
    private final REFGroceryItemQueryClient groceryItemQueryClient;

    @Override
    public void handle(REFRecognitionContext context) {
        String inputText = context.getEffectiveInput();

        mlPredictionClient.predict(inputText)
                .ifPresentOrElse(
                        prediction -> {
                            groceryItemQueryClient.getItemByName(prediction.predictedGroceryItemName())
                                    .ifPresentOrElse(
                                            item -> {
                                                REFProductRecognitionOutput output = REFProductRecognitionOutput.of(
                                                        item.groceryItemId(),
                                                        item.groceryItemName(),
                                                        item.categoryPath(),
                                                        item.representativeImageUrl()
                                                );

                                                context.getRecognition().completeWithMLPrediction(output);
                                                context.complete(output, handlerName());

                                                log.info("ML 예측 성공. input='{}', matched='{}', id={}, category='{}'",
                                                        inputText, prediction.predictedGroceryItemName(), item.groceryItemId(), item.categoryPath());
                                            },
                                            () -> {
                                                log.warn("ML 예측 결과 DB 조회 실패. input='{}', matched='{}'",
                                                        inputText, prediction.predictedGroceryItemName());
                                            }
                                    );
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
