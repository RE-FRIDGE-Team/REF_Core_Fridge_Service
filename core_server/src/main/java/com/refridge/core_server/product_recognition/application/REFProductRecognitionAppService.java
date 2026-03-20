package com.refridge.core_server.product_recognition.application;

import com.refridge.core_server.product_recognition.application.dto.command.REFRecognitionRequestCommand;
import com.refridge.core_server.product_recognition.application.dto.result.REFCachedPipelineResult;
import com.refridge.core_server.product_recognition.application.dto.result.REFRecognitionResultResponse;
import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.event.REFRecognitionCompletedEvent;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.REFProductRecognitionRepository;
import com.refridge.core_server.product_recognition.domain.service.REFRecognitionPipeline;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import com.refridge.core_server.product_recognition.infra.pipeline.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class REFProductRecognitionAppService {

    private final REFProductRecognitionRepository recognitionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final REFRecognitionPipelineCacheService pipelineCacheService;

    /* 핸들러 주입 (순서가 곧 파이프라인 순서) */
    private final REFExclusionFilterHandler exclusionFilterHandler;
    private final REFProductNameParsingHandler productNameParsingHandler;
    private final REFGroceryItemDictMatchHandler groceryItemDictMatchHandler;
    private final REFProductIndexSearchHandler productIndexSearchHandler;
    private final REFMLPredictionHandler mlPredictionHandler;

    @Transactional
    public REFRecognitionResultResponse recognize(REFRecognitionRequestCommand command) {
        // 1. AR 생성 및 저장 — 항상 실행 (인식 이력 기록)
        final REFProductRecognition recognition = recognitionRepository.save(
                REFProductRecognition.create(
                        command.inputText(),
                        command.requesterId()
                )
        );

        // 2. 캐시 확인 → 히트면 파이프라인 스킵, 미스면 실행
        REFCachedPipelineResult pipelineResult = pipelineCacheService
                .getCachedResult(command.inputText())
                .map(cached -> {
                    // 캐시 히트: AR 상태만 업데이트
                    applyResultToRecognition(recognition, cached);
                    return cached;
                })
                .orElseGet(() -> {
                    // 캐시 미스: 파이프라인 실행 + 캐시 저장
                    REFRecognitionContext ctx = executePipeline(command.inputText(), recognition);
                    REFCachedPipelineResult result = REFCachedPipelineResult.from(ctx);
                    pipelineCacheService.cacheResult(command.inputText(), result);
                    return result;
                });

        // 3. 이벤트 발행
        publishCompletionEvent(recognition, pipelineResult);

        // 4. 결과 반환
        return REFRecognitionResultResponse.from(pipelineResult);
    }

    /**
     * 파이프라인을 조립하고 실행한다.
     * 파이프라인 내부에서 AR 상태가 직접 업데이트된다.
     */
    private REFRecognitionContext executePipeline(String inputText,
                                                  REFProductRecognition recognition) {
        REFRecognitionPipeline pipeline = new REFRecognitionPipeline(List.of(
                productNameParsingHandler,
                exclusionFilterHandler,
                groceryItemDictMatchHandler,
                productIndexSearchHandler,
                mlPredictionHandler
        ));

        return pipeline.execute(new REFRecognitionContext(inputText, recognition));
    }

    /**
     * 캐시 히트 시, 캐시된 결과를 기반으로 AR 상태를 업데이트한다.
     * 파이프라인을 실행하지 않으므로 직접 AR 메서드를 호출해야 한다.
     */
    private void applyResultToRecognition(REFProductRecognition recognition,
                                          REFCachedPipelineResult result) {
        if (result.rejected()) {
            recognition.rejectAsNonFood();
        } else if (result.isNoMatch()) {
            recognition.failToMatch();
        } else {
            REFProductRecognitionOutput output = result.toOutput();
            switch (result.completedBy()) {
                case "GroceryItemDictMatch" ->
                        recognition.completeWithGroceryItemDictionaryMatch(output);
                case "ProductIndexSearch" ->
                        recognition.completeWithProductIndexMatch(output);
                case "MLPrediction" ->
                        recognition.completeWithMLPrediction(output);
                default ->
                        log.warn("알 수 없는 completedBy: {}", result.completedBy());
            }
        }
    }

    private void publishCompletionEvent(REFProductRecognition recognition,
                                        REFCachedPipelineResult result) {
        eventPublisher.publishEvent(new REFRecognitionCompletedEvent(
                recognition.getIdValue(),
                recognition.getRequesterIdValue().toString(),
                recognition.getProcessingPath(),
                result.rejected()
        ));
    }
}