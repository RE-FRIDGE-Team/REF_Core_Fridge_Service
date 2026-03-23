package com.refridge.core_server.recognition_feedback.infra.persistence.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 피드백 상세 조회 결과 Projection DTO.
 * QueryDSL에서 직접 필요한 컬럼만 SELECT하여 매핑합니다.
 */
@Builder
public record REFFeedbackDetailDto(
        // 식별자
        UUID feedbackId,
        UUID recognitionId,
        UUID requesterId,
        String statusCode,

        // 원본 스냅샷
        String origProductName,
        Long origGroceryItemId,
        String origGroceryItemName,
        String origCategoryPath,
        String origBrandName,
        Integer origQuantity,
        String origVolume,
        String origVolumeUnit,
        String origImageUrl,
        String origCompletedBy,

        // 사용자 수정 (nullable)
        String correctedProductName,
        String correctedGroceryItemName,
        String correctedCategoryPath,
        String correctedBrandName,
        Integer correctedQuantity,
        String correctedVolume,
        String correctedVolumeUnit,
        Long purchasePrice,

        // diff (nullable)
        Boolean diffProductName,
        Boolean diffGroceryItem,
        Boolean diffCategory,
        Boolean diffBrand,
        Boolean diffQuantityVolume,

        // 메타
        boolean autoApproved,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
}