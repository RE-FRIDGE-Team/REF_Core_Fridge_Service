package com.refridge.core_server.recognition_feedback.application.dto.command;

import lombok.Builder;

/**
 * 관리자 검수 항목 반려 커맨드.
 *
 * @param reviewId  반려할 검수 항목 ID
 * @param adminNote 반려 사유 (nullable이나 명확한 사유 기재 권장)
 */
@Builder
public record REFReviewRejectCommand(
        Long reviewId,
        String adminNote
) {
}
