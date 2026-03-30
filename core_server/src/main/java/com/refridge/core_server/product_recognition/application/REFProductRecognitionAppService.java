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

    // [신규 추가] 피드백 BC의 수정 이력 조회 포트
    private final REFCorrectionHistoryQueryPort correctionHistoryQueryPort;

    /** 타 사용자 수정 이력 최대 조회 건수 */
    private static final int MAX_SUGGESTION_COUNT = 3;

    @Transactional
    public REFRecognitionResultResponse recognize(REFRecognitionRequestCommand command) {
        // 1. AR 생성 및 저장
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
                    applyResultToRecognition(recognition, cached);
                    return cached;
                })
                .orElseGet(() -> {
                    REFRecognitionContext ctx = executePipeline(command.inputText(), recognition);

                    // ──────────────────────────────────────────────────────
                    // [신규 추가] 파이프라인 완료 후 Context의 파싱 결과를 AR에 저장
                    // ──────────────────────────────────────────────────────
                    recognition.applyParsedResult(ctx.getParsedProductName());

                    REFCachedPipelineResult result = REFCachedPipelineResult.from(ctx);
                    pipelineCacheService.cacheResult(command.inputText(), result);
                    return result;
                });

        // 3. 이벤트 발행 — 변경 없음
        publishCompletionEvent(recognition, pipelineResult);

        // 4. 결과 반환
        REFRecognitionResultResponse response = REFRecognitionResultResponse.from(pipelineResult);

        // ──────────────────────────────────────────────────────────────
        // [신규 추가] 타 사용자 수정 이력 조회 → 응답에 추천 정보 포함
        // 비식재료 반려건이 아닌 경우에만 조회 (반려건은 추천 대상 아님)
        // ──────────────────────────────────────────────────────────────
        if (!pipelineResult.rejected() && pipelineResult.refinedText() != null) {
            List<REFCorrectionSuggestion> suggestions = lookupCorrectionSuggestions(
                    pipelineResult.refinedText());

            if (!suggestions.isEmpty()) {
                response = response.withCorrectionSuggestions(suggestions);
            }
        }

        return response;
    }

    /**
     * 파이프라인을 조립하고 실행한다. — 변경 없음
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
     * 캐시 히트 시 AR 상태 업데이트. — 변경 없음
     */
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

    /**
     * [신규 추가] 캐시된 결과에서 REFParsedProductInformation을 복원합니다.
     */
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

    // ──────────────────────────────────────────────────────────────
    // [신규 추가] 타 사용자 수정 이력 조회
    // ──────────────────────────────────────────────────────────────

    /**
     * 피드백 BC에서 동일 원본 제품명에 대한 수정 이력을 조회하여
     * 인식 BC 언어의 {@link REFCorrectionSuggestion} 리스트로 변환합니다.
     *
     * @param originalProductName 파이프라인이 산출한 정제 제품명
     * @return 수정 추천 목록 (빈도 높은 순, 없으면 빈 리스트)
     */
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

    /**
     * 이벤트 발행 — 변경 없음
     */
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