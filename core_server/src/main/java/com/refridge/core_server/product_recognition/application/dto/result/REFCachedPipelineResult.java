package com.refridge.core_server.product_recognition.application.dto.result;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.Builder;

/**
 * 파이프라인 실행 결과의 캐시 가능한 스냅샷.
 * JPA 엔티티(AR)를 포함하지 않고 순수 값만 보유하여 Redis 직렬화에 안전합니다.
 *
 * <h3>alias 처리 방식</h3>
 * alias 교체는 파이프라인 외부(AppService)에서 응답 수준에만 적용합니다.
 * 캐시에는 항상 원본 파이프라인 결과(aliasApplied=false)를 저장합니다.
 *
 * 이유: 캐시에 alias를 저장하면 alias가 reopen(재심사)될 때
 *       stale한 alias가 Redis TTL 만료 전까지 계속 응답에 나타납니다.
 *       AppService에서 매번 Redis alias:confirmed를 조회하는 비용은
 *       O(1) HGET이므로 허용 가능합니다.
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

        /**
         * AppService가 파이프라인 완료 후 alias 조회 결과를 세팅합니다.
         * 캐시 저장 시에는 항상 false입니다.
         */
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
                .aliasApplied(false)  // 캐시 저장 시 항상 false
                .build();
    }

    /**
     * alias 적용 후 응답용 새 인스턴스를 반환합니다.
     * refinedText를 aliasName으로 교체하고 aliasApplied = true로 마킹합니다.
     * 이 인스턴스는 응답 생성에만 사용되며 캐시에 저장되지 않습니다.
     *
     * @param aliasName 확정된 alias 제품명
     */
    public REFCachedPipelineResult withAliasApplied(String aliasName) {
        return REFCachedPipelineResult.builder()
                .groceryItemId(this.groceryItemId)
                .groceryItemName(this.groceryItemName)
                .categoryPath(this.categoryPath)
                .imageUrl(this.imageUrl)
                .completedBy(this.completedBy)
                .rejected(this.rejected)
                .rejectedReason(this.rejectedReason)
                .originalText(this.originalText)
                .refinedText(aliasName)      // alias로 교체
                .brandName(this.brandName)
                .quantity(this.quantity)
                .volume(this.volume)
                .volumeUnit(this.volumeUnit)
                .aliasApplied(true)          // alias 적용 마킹
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