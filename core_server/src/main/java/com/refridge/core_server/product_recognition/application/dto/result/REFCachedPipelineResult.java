package com.refridge.core_server.product_recognition.application.dto.result;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.Builder;

/**
 * <pre>
 * 파이프라인 실행 결과의 캐시 가능한 스냅샷.
 * JPA 엔티티(AR)를 포함하지 않고 순수 값만 보유하여 Redis 직렬화에 안전합니다.
 *
 * aliasApplied 필드:
 *   파싱 단계에서 alias 교체가 일어났는지 여부입니다.
 *   캐시 히트 시에도 이 값을 보존하여 AppService가
 *   correctionSuggestions 조회 생략 여부를 올바르게 판단할 수 있도록 합니다.</pre>
 */
@Builder
public record REFCachedPipelineResult(
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String imageUrl,

        String completedBy,
        boolean rejected,
        String rejectedReason,

        String originalText,
        String refinedText,
        String brandName,
        Integer quantity,
        String volume,
        String volumeUnit,

        /** 파싱 단계에서 alias 교체 여부 - correctionSuggestions 조회 생략 판단용 */
        boolean aliasApplied
) {

    public static REFCachedPipelineResult from(REFRecognitionContext ctx) {
        REFProductRecognitionOutput output = ctx.getOutput();
        REFParsedProductInformation parsed = ctx.getParsedProductName();

        return REFCachedPipelineResult.builder()
                .groceryItemId(output != null ? output.getGroceryItemId() : null)
                .groceryItemName(output != null ? output.getGroceryItemName() : null)
                .categoryPath(output != null ? output.getCategoryPath() : null)
                .imageUrl(output != null ? output.getImageUrl() : null)
                .completedBy(ctx.getCompletedBy())
                .rejected(ctx.isCompleted() && output == null
                        && !"MLPrediction:NO_MATCH".equals(ctx.getCompletedBy()))
                .rejectedReason(ctx.getRecognition().getRejectionDetail().getMatchedKeyword())
                .originalText(parsed != null ? parsed.originalText() : null)
                .refinedText(parsed != null ? parsed.refinedText() : null)
                .brandName(parsed != null ? parsed.brandName() : null)
                .quantity(parsed != null ? parsed.getQuantity().orElse(null) : null)
                .volume(parsed != null ? parsed.getVolume().orElse(null) : null)
                .volumeUnit(parsed != null ? parsed.volumeUnit() : null)
                .aliasApplied(ctx.isAliasApplied())
                .build();
    }

    public REFProductRecognitionOutput toOutput() {
        if (groceryItemId == null) return null;
        return REFProductRecognitionOutput.of(
                groceryItemId, groceryItemName, categoryPath, imageUrl);
    }

    public boolean isNoMatch() {
        return !rejected && groceryItemId == null;
    }

    public boolean isSuccess() {
        return groceryItemId != null;
    }
}