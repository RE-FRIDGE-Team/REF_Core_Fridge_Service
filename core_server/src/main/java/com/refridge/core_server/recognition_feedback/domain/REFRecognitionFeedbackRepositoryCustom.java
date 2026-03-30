package com.refridge.core_server.recognition_feedback.domain;

import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackAggregationDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackCorrectionHistoryDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackDetailDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackSummaryDto;

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

    /**
     * 동일 원본 제품명(orig_product_name)에 대해 CORRECTED 상태인 피드백들의
     * 수정 내용을 집계하여 반환합니다.
     * <p>
     * 동일한 수정 조합(제품명+식재료+브랜드+카테고리)은 GROUP BY로 묶어
     * occurrenceCount로 빈도를 제공합니다. 빈도 높은 순 정렬.
     * <p>
     * 사용 시점: recognize() 응답에 "타 사용자 수정 추천" 포함 시.
     *
     * @param originalProductName 원본 정제 제품명
     * @param limit               최대 반환 건수
     * @return 수정 이력 목록 (빈도 높은 순)
     */
    List<REFFeedbackCorrectionHistoryDto> findCorrectionHistoryByProductName(
            String originalProductName, int limit);
}