package com.refridge.core_server.recognition_feedback.application.dto.query;

import lombok.Builder;

import java.util.UUID;

/**
 * 특정 사용자의 피드백 목록 조회 쿼리.
 *
 * @param requesterId 요청자 ID
 * @param statusCode  상태 필터 (nullable — null이면 전체 조회). P/A/C 중 하나.
 */
@Builder
public record REFFeedbackListQuery(
        UUID requesterId,
        String statusCode
) {
}