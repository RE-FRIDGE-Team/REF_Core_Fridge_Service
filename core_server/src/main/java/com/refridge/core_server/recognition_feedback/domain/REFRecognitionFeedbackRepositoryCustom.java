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
     * Redis 트랙 1 브랜드 카운터 만료 후 복원에 사용됩니다.
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

    /**
     * 특정 원본 제품명에 대해 APPROVED 상태인 피드백 수를 조회합니다.
     *
     * @param originalProductName 원본 정제 제품명 (orig_product_name)
     * @return APPROVED 상태 피드백 수
     */
    long countApprovedByOriginalProductName(String originalProductName);

    /**
     * 특정 원본 식재료명에 대해 APPROVED 상태인 피드백 수를 조회합니다.
     *
     * @param originalGroceryItemName 원본 식재료명 (orig_grocery_item_name)
     * @return APPROVED 상태 피드백 수
     */
    long countApprovedByOriginalGroceryItemName(String originalGroceryItemName);

    // ── 트랙 2 브랜드 교체 전용 ─────────────────────────────────────────────

    /**
     * 특정 원본 브랜드에 대해 사용자가 입력한 교체 브랜드별 집계를 반환합니다.
     * <p>
     * CORRECTED 상태 피드백 중 {@code originalBrandName}이 일치하는 건을
     * {@code correctedBrandName} 기준으로 GROUP BY하여 횟수 내림차순으로 반환합니다.
     * </p>
     * <p>
     * 트랙 2(기존 브랜드 교체) Redis miss 복원 시 Hash 전체를 재구성하는 데 사용됩니다.
     * </p>
     *
     * @param originalBrandName 원본 인식 결과의 브랜드명 (orig_brand_name)
     * @return 교체 브랜드별 집계 (횟수 내림차순)
     */
    List<REFFeedbackBrandCorrectionCountByOriginalDto> findBrandCorrectionCountsByOriginalBrand(
            String originalBrandName);

    /**
     * 특정 원본 브랜드명에 대해 APPROVED 상태인 피드백 수를 반환합니다.
     * <p>
     * 트랙 2 Redis miss 복원 시 {@code __total__} 필드를 재구성할 때
     * CORRECTED 합계에 합산하여 Gate 2 비율 과대 계산을 방지합니다.
     * </p>
     *
     * @param originalBrandName 원본 인식 결과의 브랜드명 (orig_brand_name)
     * @return APPROVED 상태 피드백 수
     */
    long countApprovedByOriginalBrandName(String originalBrandName);
}