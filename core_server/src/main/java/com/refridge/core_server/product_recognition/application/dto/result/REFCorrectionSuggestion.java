package com.refridge.core_server.product_recognition.application.dto.result;

import lombok.Builder;

/**
 * 타 사용자가 동일 제품명에 대해 수정한 이력을 인식 결과 응답에 포함할 때 사용하는 DTO.
 * <p>
 * 피드백 BC의 {@code REFFeedbackCorrectionHistoryDto}를 인식 BC의 언어로 변환한 것입니다.
 * 클라이언트는 이 정보를 바탕으로 "파이프라인 인식 결과 vs 타 사용자 수정본" 선택 UI를 제공합니다.
 *
 * @param correctedProductName     수정된 제품명
 * @param correctedGroceryItemName 수정된 식재료명
 * @param correctedBrandName       수정된 브랜드명
 * @param correctedCategoryPath    수정된 카테고리 경로
 * @param occurrenceCount          동일 수정이 반복된 횟수 (신뢰도 지표)
 */
@Builder
public record REFCorrectionSuggestion(
        String correctedProductName,
        String correctedGroceryItemName,
        String correctedBrandName,
        String correctedCategoryPath,
        long occurrenceCount
) {
}