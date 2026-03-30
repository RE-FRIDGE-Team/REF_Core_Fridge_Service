package com.refridge.core_server.recognition_feedback.infra.persistence.dto;

import lombok.Builder;

/**
 * 동일 제품명에 대한 타 사용자 수정 이력 Projection DTO.
 * <p>
 * 인식 결과 응답 시 "이전 사용자가 이렇게 수정했는데 맞나요?" 추천에 사용됩니다.
 * 제품명, 식재료명, 브랜드만 포함 — 수량/가격/용량은 사용자마다 다를 수 있으므로 제외.
 *
 * @param correctedProductName     수정된 제품명
 * @param correctedGroceryItemName 수정된 식재료명
 * @param correctedBrandName       수정된 브랜드명
 * @param correctedCategoryPath    수정된 카테고리 경로
 * @param occurrenceCount          동일 수정이 반복된 횟수
 */
@Builder
public record REFFeedbackCorrectionHistoryDto(
        String correctedProductName,
        String correctedGroceryItemName,
        String correctedBrandName,
        String correctedCategoryPath,
        long occurrenceCount
) {
}