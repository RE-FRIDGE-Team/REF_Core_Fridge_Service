package com.refridge.core_server.recognition_feedback.domain;

import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface REFRecognitionFeedbackRepositoryCustom {

    Optional<REFFeedbackDetailDto> findDetailByFeedbackId(UUID feedbackId);
    Optional<REFFeedbackDetailDto> findDetailByRecognitionId(UUID recognitionId);
    List<REFFeedbackSummaryDto> findSummariesByRequesterId(UUID requesterId, String statusCode);
    Optional<REFFeedbackAggregationDto> countAggregationByProductName(String productName);
    List<REFRecognitionFeedbackId> findPendingIdsCreatedBefore(LocalDateTime createdBefore, int limit);
    List<REFFeedbackCorrectionHistoryDto> findCorrectionHistoryByProductName(
            String originalProductName, int limit);

    /**
     * 특정 브랜드명으로 수정된 CORRECTED 피드백 수를 조회합니다.
     * Redis 브랜드 카운터 만료 후 복원에 사용됩니다.
     *
     * @param correctedBrandName 사용자가 수정한 브랜드명
     * @return CORRECTED 상태 피드백 중 해당 브랜드명으로 수정된 건수
     */
    long countByCorrectBrandName(String correctedBrandName);

    /**
     * 특정 원본 제품명에 대해 CORRECTED 상태인 피드백들의
     * 수정 제품명별 선택 횟수를 조회합니다.
     * Redis alias 후보 Hash 만료 후 복원에 사용됩니다.
     *
     * @param originalProductName 원본 정제 제품명 (orig_product_name)
     * @return 수정 제품명 → 선택 횟수 목록 (횟수 내림차순)
     */
    List<REFFeedbackBrandCorrectionCountDto> findAliasCandidateCountsByOriginalName(
            String originalProductName);

    /**
     * 특정 원본 식재료명에 대해 CORRECTED 상태인 피드백들의
     * 수정 식재료명별 선택 횟수를 조회합니다.
     * Redis grocery-item-mapping 후보 Hash 만료 후 복원에 사용됩니다.
     *
     * @param originalGroceryItemName 원본 식재료명 (orig_grocery_item_name)
     * @return 수정 식재료명 → 선택 횟수 목록 (횟수 내림차순)
     */
    List<REFFeedbackGroceryItemMappingCountDto> findGroceryItemMappingCountsByOriginalName(
            String originalGroceryItemName);
}