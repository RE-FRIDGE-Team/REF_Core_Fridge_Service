package com.refridge.core_server.recognition_feedback.application.dto.command;

import lombok.Builder;

import java.util.UUID;

/**
 * 사용자가 인식 결과를 승인할 때 사용하는 커맨드.
 *
 * @param feedbackId    승인할 피드백 ID
 * @param purchasePrice 구매 가격 (nullable — 입력하지 않을 수도 있음)
 */
@Builder
public record REFFeedbackApproveCommand(
        UUID feedbackId,
        Long purchasePrice
) {
}