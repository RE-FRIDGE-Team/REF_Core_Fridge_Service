package com.refridge.core_server.product_recognition.application.dto.result;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductName;

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
        String imageUrl
) {

    public static REFRecognitionResultResponse from(REFRecognitionContext ctx) {
        REFParsedProductName parsed = ctx.getParsedProductName();
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
                output != null ? output.getImageUrl() : null
        );
    }

}
