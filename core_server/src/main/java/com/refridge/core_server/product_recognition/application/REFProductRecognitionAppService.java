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

/**
 * 제품명 인식 파이프라인 Application Service입니다.
 *
 * <h3>파이프라인 순서</h3>
 * <pre>
 *   1. ProductNameParsing  — 브랜드/수량/용량 추출, 제품명 정제
 *   2. ExclusionFilter     — 비식재료 사전 필터 (샴푸, 세제 등 조기 차단)
 *   3. ProductIndexSearch  — 등록된 Product DB 검색 (가장 빠른 정확 매칭)
 *   4. GroceryItemDictMatch — 식재료 사전 매칭 (Aho-Corasick)
 *   5. MLPrediction        — ML 모델 예측 (최후 수단)
 * </pre>
 *
 * <h3>ProductIndexSearch를 GroceryItemDictMatch보다 앞에 두는 이유</h3>
 * <p>
 * 관리자가 카테고리 재분류를 승인하면 {@code REFCategoryChangeOnApprovalEventHandler}가
 * 수정된 카테고리로 신규 Product를 등록합니다.
 * ProductIndexSearch가 GroceryItemDict보다 앞에 있어야
 * 다음 인식 시 이 Product를 우선 찾아 올바른 카테고리로 매칭합니다.
 * ProductIndexSearch 이전에 GroceryItemDictMatch가 실행되면
 * 오래된 사전 정보로 잘못 매칭될 수 있습니다.
 * </p>
 *
 * <h3>alias 처리 방식</h3>
 * <p>
 * alias 교체는 파이프라인 내부가 아닌 응답 수준에서만 수행합니다.
 * 파이프라인 중간에 alias로 refinedText를 교체하면
 * 이미 "원본 제품명"으로 등록된 Product를 ProductIndexSearcher가 찾지 못하는
 * 역설적 상황이 발생합니다.
 * </p>
 */
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
     * <p>
     * 파이프라인 내부에서 alias를 교체하지 않는 이유:
     * </p>
     * <ol>
     *   <li>기존 Product("해찬들 초고추장 340ml")를 ProductIndexSearch가 찾아야 함</li>
     *   <li>searchByProductName()의 forwardLike/reverseLike가 원본명으로 커버</li>
     *   <li>AR 저장, 피드백 집계는 원본명 기준 유지 (이력 일관성)</li>
     * </ol>
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
        REFCachedPipelineResult responseResult = applyAliasToResult(pipelineResult);

        // 5. 응답 생성
        REFRecognitionResultResponse response =
                REFRecognitionResultResponse.from(responseResult);

        // ── 6. correctionSuggestions 조회 ────────────────────────────
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
     * <p>
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

    /**
     * 파이프라인을 실행합니다.
     *
     * <h3>핸들러 순서 (변경됨)</h3>
     * <pre>
     *   Parsing → Exclusion → ProductIndexSearch → GroceryItemDictMatch → MLPrediction
     * </pre>
     * <p>
     * ProductIndexSearch를 GroceryItemDictMatch보다 앞에 두어
     * 관리자 카테고리 재분류 승인으로 등록된 Product를 우선 매칭합니다.
     * </p>
     */
    private REFRecognitionContext executePipeline(String inputText,
                                                  REFProductRecognition recognition) {
        REFRecognitionPipeline pipeline = new REFRecognitionPipeline(List.of(
                productNameParsingHandler,      // 1. 브랜드/수량/용량 추출, 제품명 정제
                exclusionFilterHandler,         // 2. 비식재료 사전 필터
                productIndexSearchHandler,      // 3. 등록된 Product 검색 (우선순위 상향)
                groceryItemDictMatchHandler,    // 4. 식재료 사전 매칭
                mlPredictionHandler             // 5. ML 모델 예측 (최후 수단)
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
        recognition.applyParsedResult(rebuildParsedInfoFromCache(result));
    }

    private com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation
    rebuildParsedInfoFromCache(REFCachedPipelineResult cached) {
        return com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation
                .builder()
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
