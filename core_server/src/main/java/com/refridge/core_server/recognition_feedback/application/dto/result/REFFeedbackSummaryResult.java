package com.refridge.core_server.recognition_feedback.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 피드백 요약 조회 결과.
 * 사용자별 피드백 목록에서 사용됩니다.
 */
public record REFFeedbackSummaryResult(
        UUID feedbackId,
        UUID recognitionId,
        String status,
        String statusKorName,
        String originalProductName,
        String originalGroceryItemName,
        String originalCategoryPath,
        String completedByHandler,
        boolean autoApproved,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
}