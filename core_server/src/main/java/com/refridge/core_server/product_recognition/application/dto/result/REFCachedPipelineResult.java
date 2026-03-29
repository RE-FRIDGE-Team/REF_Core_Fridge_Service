package com.refridge.core_server.product_recognition.application.dto.result;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.Builder;

/**
 * 파이프라인 실행 결과의 캐시 가능한 스냅샷.
 * JPA 엔티티(AR)를 포함하지 않고 순수 값만 보유하여 Redis 직렬화에 안전하다.
 */
@Builder
public record REFCachedPipelineResult(
        // 파이프라인 출력
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String imageUrl,

        // 파이프라인 메타데이터
        String completedBy,
        boolean rejected,
        String rejectedReason,

        // 파싱 정보
        String originalText,
        String refinedText,
        String brandName,
        Integer quantity,
        String volume,
        String volumeUnit
) {

    /**
     * 파이프라인 실행 직후 Context로부터 캐시 가능한 스냅샷을 생성한다.
     */
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
                .build();
    }

    /**
     * 캐시된 결과로부터 AR 상태 업데이트용 Output을 복원한다.
     * rejected이거나 NO_MATCH인 경우 null 반환.
     */
    public REFProductRecognitionOutput toOutput() {
        if (groceryItemId == null) {
            return null;
        }
        return REFProductRecognitionOutput.of(
                groceryItemId, groceryItemName, categoryPath, imageUrl
        );
    }

    public boolean isNoMatch() {
        return !rejected && groceryItemId == null;
    }

    public boolean isSuccess() {
        return groceryItemId != null;
    }
}