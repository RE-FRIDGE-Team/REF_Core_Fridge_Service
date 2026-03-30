package com.refridge.core_server.product_recognition.application.dto.result;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;

import java.util.List;

public record REFRecognitionResultResponse(
        boolean success,
        boolean rejected,           // 비식재료 반려 여부
        String completedBy,         // 어느 단계에서 완료됐는지
        String refinedProductName,  // 정제된 제품명 (파싱 결과)
        String brandName,           // 추출된 브랜드명
        Integer quantity,           // 추출된 수량
        String volume,              // 추출된 용량
        Long groceryItemId,
        String groceryItemName,
        String categoryPath,
        String imageUrl,
        List<REFCorrectionSuggestion> correctionSuggestions

) {

    public static REFRecognitionResultResponse from(REFRecognitionContext ctx) {
        REFParsedProductInformation parsed = ctx.getParsedProductName();
        var output = ctx.getOutput();
        boolean rejected = ctx.isCompleted() && output == null;

        return new REFRecognitionResultResponse(
                ctx.isCompleted(),
                rejected,
                ctx.getCompletedBy(),
                parsed != null ? parsed.refinedText() : ctx.getRawInput(),
                parsed != null ? parsed.getBrandName().orElse(null) : null,
                parsed != null ? parsed.getQuantity().orElse(null) : null,
                parsed != null ? parsed.getVolume().orElse(null) : null,
                output != null ? output.getGroceryItemId() : null,
                output != null ? output.getGroceryItemName() : null,
                output != null ? output.getCategoryPath() : null,
                output != null ? output.getImageUrl() : null,
                List.of()
        );
    }

    /**
     * 캐시된 파이프라인 결과로부터 응답 생성
     */
    public static REFRecognitionResultResponse from(REFCachedPipelineResult cached) {
        return new REFRecognitionResultResponse(
                true,
                cached.rejected(),
                cached.completedBy(),
                cached.refinedText() != null ? cached.refinedText() : cached.originalText(),
                cached.brandName(),
                cached.quantity(),
                cached.volume(),
                cached.groceryItemId(),
                cached.groceryItemName(),
                cached.categoryPath(),
                cached.imageUrl(),
                List.of()
        );
    }

    public REFRecognitionResultResponse withCorrectionSuggestions(
            List<REFCorrectionSuggestion> suggestions) {
        return new REFRecognitionResultResponse(
                this.success,
                this.rejected,
                this.completedBy,
                this.refinedProductName,
                this.brandName,
                this.quantity,
                this.volume,
                this.groceryItemId,
                this.groceryItemName,
                this.categoryPath,
                this.imageUrl,
                suggestions
        );
    }

    public boolean hasCorrectionSuggestions() {
        return correctionSuggestions != null && !correctionSuggestions.isEmpty();
    }

    public static REFRecognitionResultResponse failure(String rawInput, String reason) {
        return new REFRecognitionResultResponse(
                false, false, "FAILED:" + reason,
                rawInput, null, null, null,
                null, null, null, null, List.of()
        );
    }
}
