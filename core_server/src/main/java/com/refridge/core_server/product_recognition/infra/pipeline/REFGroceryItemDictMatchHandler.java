package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFGroceryItemDictionaryMatcher;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 식재료 사전 매칭 핸들러.
 * 정제된 제품명이 식재료 사전에 포함되어 있는지 확인한다.
 * NOTE: 매칭된 GroceryItemName으로 GroceryItem 상세를 조회해야 하나,
 *       현재는 GroceryItem Context와의 연동이 미구현 상태.
 *       포트를 통해 조회하도록 추후 확장 필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemDictMatchHandler implements REFRecognitionHandler {

    private final REFGroceryItemDictionaryMatcher groceryItemDictionaryMatcher;


    @Override
    public void handle(REFRecognitionContext context) {
        String inputText = context.getEffectiveInput();

        groceryItemDictionaryMatcher.findMatch(inputText)
                .ifPresent(matchInfo -> {
                    // TODO: GroceryItem Context 포트를 통해 상세 정보 조회 필요
                    // REFProductRecognitionOutput output = groceryItemQueryPort.findByName(matchInfo.matchedGroceryItemName())
                    //         .map(item -> REFProductRecognitionOutput.of(...))
                    //         .orElse(null);
                    // 현재는 임시로 이름만 세팅
                    REFProductRecognitionOutput output = REFProductRecognitionOutput.of(
                            0L, // TODO: 실제 ID로 교체
                            matchInfo.matchedGroceryItemName(),
                            "UNKNOWN", // TODO: 실제 카테고리로 교체
                            null
                    );
                    context.getRecognition().completeWithGroceryItemDictionaryMatch(output);
                    context.complete(output, handlerName());

                    log.info("식재료 사전 매칭 성공. input='{}', matched='{}'",
                            inputText, matchInfo.matchedGroceryItemName());
                });
    }

    @Override
    public String handlerName() {
        return "GroceryItemDictMatch";
    }
}
