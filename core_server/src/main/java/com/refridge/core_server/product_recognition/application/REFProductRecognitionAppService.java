package com.refridge.core_server.product_recognition.application;

import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
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
import java.util.Optional;

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

    /**
     * alias 조회는 파이프라인 완료 후 응답 수준에서만 수행합니다.
     *
     * 파이프라인 내부에서 alias를 교체하지 않는 이유:
     *   1. 기존 Product("해찬들 초고추장 340ml")를 ProductIndexSearch가 찾아야 함
     *   2. searchByProductName()의 forwardLike/reverseLike가 원본명으로 커버
     *   3. AR 저장, 피드백 집계는 원본명 기준 유지 (이력 일관성)
     *
     * Product 등록 시에는 REFProductFeedbackAggregationEventHandler에서 alias 적용.
     */
    private final REFAliasConfirmationService aliasConfirmationService;

    private static final int MAX_SUGGESTION_COUNT = 3;

    @Transactional
    public REFRecognitionResultResponse recognize(REFRecognitionRequestCommand command) {
        // 1. AR 생성 및 저장
        final REFProductRecognition recognition = recognitionRepository.save(
                REFProductRecognition.create(command.inputText(), command.requesterId()));

        // 2. 캐시 확인 → 히트면 파이프라인 스킵
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

        // 3. 이벤트 발행
        publishCompletionEvent(recognition, pipelineResult);

        // ── 4. 응답 수준 alias 교체 ──────────────────────────────────
        // 파이프라인은 원본 refinedText("해찬들 초고추장 340ml")로 완료됨
        // 응답에서만 alias("해찬들 초고추장")로 교체
        // AR 저장, 피드백 집계, Product 탐색은 원본명 기준 유지
        REFCachedPipelineResult responseResult = applyAliasToResult(pipelineResult);

        // 5. 응답 생성
        REFRecognitionResultResponse response =
                REFRecognitionResultResponse.from(responseResult);

        // ── 6. correctionSuggestions 조회 ────────────────────────────
        // alias 적용 시: 이미 검증된 올바른 이름 → suggestions 생략
        // 비식재료 반려: suggestions 조회 대상 아님
        // 원본명 기준으로 조회 (alias 교체 전 refinedText 사용)
        if (!responseResult.rejected()
                && !responseResult.aliasApplied()
                && pipelineResult.refinedText() != null) {

            List<REFCorrectionSuggestion> suggestions =
                    lookupCorrectionSuggestions(pipelineResult.refinedText());

            if (!suggestions.isEmpty()) {
                response = response.withCorrectionSuggestions(suggestions);
            }
        }

        return response;
    }

    /**
     * 파이프라인 완료 후 응답 수준에서 alias를 적용합니다.
     *
     * 캐시 저장은 항상 원본(aliasApplied=false)으로 유지합니다.
     * alias reopen 시 stale 캐시 문제를 방지하기 위해
     * alias 교체된 결과는 캐시에 저장하지 않습니다.
     */
    private REFCachedPipelineResult applyAliasToResult(REFCachedPipelineResult pipelineResult) {
        if (pipelineResult.rejected() || pipelineResult.refinedText() == null) {
            return pipelineResult;
        }

        Optional<String> aliasOpt = aliasConfirmationService
                .findConfirmedAlias(pipelineResult.refinedText());

        if (aliasOpt.isEmpty()) {
            return pipelineResult;
        }

        String aliasName = aliasOpt.get();
        log.info("[AppService] 응답 alias 교체. '{}' → '{}'",
                pipelineResult.refinedText(), aliasName);

        return pipelineResult.withAliasApplied(aliasName);
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
                default -> log.warn("알 수 없는 completedBy: {}", result.completedBy());
            }
        }
        // AR에는 항상 원본 파이프라인 결과 저장 (alias 미적용)
        // 피드백 집계(orig_product_name), 긍정 카운터 모두 원본명 기준 유지
        recognition.applyParsedResult(rebuildParsedInfoFromCache(result));
    }

    private com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation
    rebuildParsedInfoFromCache(REFCachedPipelineResult cached) {
        return com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation
                .builder()
                .originalText(cached.originalText())
                .refinedText(cached.refinedText())  // 원본명 유지
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