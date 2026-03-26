package com.refridge.core_server.recognition_feedback.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 피드백 상세 조회 결과.
 * Presentation 레이어에서 클라이언트 응답으로 변환됩니다.
 */
public record REFFeedbackDetailResult(
        // 식별자
        UUID feedbackId,
        UUID recognitionId,
        String status,
        String statusKorName,

        // 원본 인식 결과
        String originalProductName,
        Long originalGroceryItemId,
        String originalGroceryItemName,
        String originalCategoryPath,
        String originalBrandName,
        Integer originalQuantity,
        String originalVolume,
        String originalVolumeUnit,
        String originalImageUrl,
        String completedByHandler,

        // 사용자 수정 (CORRECTED인 경우)
        String correctedProductName,
        String correctedGroceryItemName,
        String correctedCategoryPath,
        String correctedBrandName,
        Integer correctedQuantity,
        String correctedVolume,
        String correctedVolumeUnit,
        Long purchasePrice,

        // diff 요약 (CORRECTED인 경우)
        Boolean productNameChanged,
        Boolean groceryItemChanged,
        Boolean categoryChanged,
        Boolean brandChanged,
        Boolean quantityVolumeChanged,

        // 메타
        boolean autoApproved,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
}