package com.refridge.core_server.product_recognition.application;

import com.refridge.core_server.product_recognition.application.dto.command.REFRecognitionRequestCommand;
import com.refridge.core_server.product_recognition.application.dto.result.REFCachedPipelineResult;
import com.refridge.core_server.product_recognition.application.dto.result.REFCorrectionSuggestion;
import com.refridge.core_server.product_recognition.application.dto.result.REFRecognitionResultResponse;
import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.event.REFRecognitionCompletedEvent;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.REFProductRecognitionRepository;
import com.refridge.core_server.product_recognition.domain.service.REFRecognitionPipeline;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import com.refridge.core_server.product_recognition.infra.pipeline.*;
import com.refridge.core_server.recognition_feedback.domain.port.REFCorrectionHistoryQueryPort;
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

    private final REFExclusionFilterHandler exclusionFilterHandler;
    private final REFProductNameParsingHandler productNameParsingHandler;
    private final REFGroceryItemDictMatchHandler groceryItemDictMatchHandler;
    private final REFProductIndexSearchHandler productIndexSearchHandler;
    private final REFMLPredictionHandler mlPredictionHandler;

    private final REFCorrectionHistoryQueryPort correctionHistoryQueryPort;

    private static final int MAX_SUGGESTION_COUNT = 3;

    @Transactional
    public REFRecognitionResultResponse recognize(REFRecognitionRequestCommand command) {
        final REFProductRecognition recognition = recognitionRepository.save(
                REFProductRecognition.create(command.inputText(), command.requesterId()));

        REFCachedPipelineResult pipelineResult = pipelineCacheService
                .getCachedResult(command.inputText())
                .map(cached -> {
                    applyResultToRecognition(recognition, cached);
                    return cached;
                })
                .orElseGet(() -> {
                    REFRecognitionContext ctx = executePipeline(command.inputText(), recognition);
                    recognition.applyParsedResult(ctx.getParsedProductName());
                    REFCachedPipelineResult result = REFCachedPipelineResult.from(ctx);
                    pipelineCacheService.cacheResult(command.inputText(), result);
                    return result;
                });

        publishCompletionEvent(recognition, pipelineResult);

        REFRecognitionResultResponse response = REFRecognitionResultResponse.from(pipelineResult);

        // alias 교체가 적용된 경우 correctionSuggestions 조회 생략
        // 이유: alias 확정 = 인식 결과가 이미 검증된 올바른 값
        //       사용자에게 "다른 사람이 이렇게 수정했어요"를 보여주면 혼란을 유발
        if (!pipelineResult.rejected()
                && !pipelineResult.aliasApplied()
                && pipelineResult.refinedText() != null) {

            List<REFCorrectionSuggestion> suggestions =
                    lookupCorrectionSuggestions(pipelineResult.refinedText());

            if (!suggestions.isEmpty()) {
                response = response.withCorrectionSuggestions(suggestions);
            }
        }

        return response;
    }

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

    private void applyResultToRecognition(REFProductRecognition recognition,
                                          REFCachedPipelineResult result) {
        if (result.rejected()) {
            recognition.rejectAsNonFood(result.rejectedReason());
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
        recognition.applyParsedResult(rebuildParsedInfoFromCache(result));
    }

    private com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation
    rebuildParsedInfoFromCache(REFCachedPipelineResult cached) {
        return com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation.builder()
                .originalText(cached.originalText())
                .refinedText(cached.refinedText())
                .brandName(cached.brandName())
                .quantity(cached.quantity())
                .volume(cached.volume())
                .volumeUnit(cached.volumeUnit())
                .build();
    }

    private List<REFCorrectionSuggestion> lookupCorrectionSuggestions(String originalProductName) {
        return correctionHistoryQueryPort
                .findByProductName(originalProductName, MAX_SUGGESTION_COUNT)
                .stream()
                .map(dto -> REFCorrectionSuggestion.builder()
                        .correctedProductName(dto.correctedProductName())
                        .correctedGroceryItemName(dto.correctedGroceryItemName())
                        .correctedBrandName(dto.correctedBrandName())
                        .correctedCategoryPath(dto.correctedCategoryPath())
                        .occurrenceCount(dto.occurrenceCount())
                        .build())
                .toList();
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