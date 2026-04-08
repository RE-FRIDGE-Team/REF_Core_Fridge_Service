package com.refridge.core_server.recognition_feedback.infra.persistence.dto;

/**
 * 원본 식재료명 기준 수정 식재료명별 선택 횟수 Projection DTO.
 *
 * 사용 시점:
 *   Redis grocery-item-mapping 후보 Hash(feedback:grocery-item-mapping:{originalName})가
 *   TTL 만료 후 첫 접근 시 DB에서 실제 누적 횟수를 복원할 때 사용합니다.
 *
 * @param originalGroceryItemName  원본 식재료명
 * @param correctedGroceryItemName 사용자가 수정한 식재료명
 * @param selectionCount           해당 수정본을 선택한 횟수
 */
public record REFFeedbackGroceryItemMappingCountDto(
        String originalGroceryItemName,
        String correctedGroceryItemName,
        long selectionCount
) {
}