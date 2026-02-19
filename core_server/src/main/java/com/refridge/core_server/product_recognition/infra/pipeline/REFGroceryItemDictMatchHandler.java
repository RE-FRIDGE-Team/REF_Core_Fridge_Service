package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFGroceryItemDictionaryMatcher;
import com.refridge.core_server.product_recognition.domain.port.REFGroceryItemQueryClient;
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

    private final REFGroceryItemQueryClient groceryItemQueryClient;

    @Override
    public void handle(REFRecognitionContext context) {
        String inputText = context.getEffectiveInput();

        groceryItemDictionaryMatcher.findMatch(inputText)
                .ifPresent(matchInfo -> {
                    String matchedName = matchInfo.matchedGroceryItemName();

                    // GroceryItem 상세 정보 조회
                    groceryItemQueryClient.getItemByName(matchedName)
                            .ifPresentOrElse(
                                    item -> {
                                        REFProductRecognitionOutput output = REFProductRecognitionOutput.of(
                                                item.groceryItemId(),
                                                item.groceryItemName(),
                                                item.categoryPath(),
                                                item.representativeImageUrl()
                                        );

                                        context.getRecognition().completeWithGroceryItemDictionaryMatch(output);
                                        context.complete(output, handlerName());

                                        log.info("식재료 사전 매칭 성공. input='{}', matched='{}', id={}, category='{}'",
                                                inputText, matchedName, item.groceryItemId(), item.categoryPath());
                                    },
                                    () -> {
                                        log.warn("식재료 사전에서 매칭되었으나 DB 조회 실패. input='{}', matched='{}'",
                                                inputText, matchedName);
                                    }
                            );
                });
    }

    @Override
    public String handlerName() {
        return "GroceryItemDictMatch";
    }
}
