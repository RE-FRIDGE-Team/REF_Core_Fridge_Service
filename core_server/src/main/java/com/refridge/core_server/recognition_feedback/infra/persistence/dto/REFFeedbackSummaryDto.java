package com.refridge.core_server.recognition_feedback.infra.persistence.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 피드백 요약 조회 결과 Projection DTO.
 * 사용자별 피드백 목록 조회 시 사용합니다.
 */
@Builder
public record REFFeedbackSummaryDto(
        UUID feedbackId,
        UUID recognitionId,
        String statusCode,
        String origProductName,
        String origGroceryItemName,
        String origCategoryPath,
        String origCompletedBy,
        boolean autoApproved,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
}