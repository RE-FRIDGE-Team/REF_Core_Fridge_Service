package com.refridge.core_server.recognition_feedback.infra.persistence.dto;

/**
 * 원본 제품명 기준 수정 제품명별 선택 횟수 Projection DTO.
 *
 * 사용 시점:
 *   Redis alias 후보 Hash(feedback:product-alias:{originalName})가
 *   TTL 만료 후 첫 접근 시 DB에서 실제 누적 횟수를 복원할 때 사용합니다.
 *
 * @param originalProductName 원본 정제 제품명
 * @param correctedProductName 사용자가 수정한 제품명
 * @param selectionCount 해당 수정본을 선택한 횟수
 */
public record REFFeedbackBrandCorrectionCountDto(
        String originalProductName,
        String correctedProductName,
        long selectionCount
) {
}