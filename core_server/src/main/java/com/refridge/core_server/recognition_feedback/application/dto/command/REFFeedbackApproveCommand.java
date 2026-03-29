package com.refridge.core_server.recognition_feedback.application.dto.command;

import lombok.Builder;

import java.util.UUID;

/**
 * 사용자가 인식 결과를 승인할 때 사용하는 커맨드.
 *
 * @param recognitionId 인식 결과 ID (피드백이 없으면 Lazy Creation)
 * @param purchasePrice 구매 가격 (nullable)
 */
@Builder
public record REFFeedbackApproveCommand(
        UUID recognitionId,
        Long purchasePrice
) {
}
