package com.refridge.core_server.recognition_feedback.domain;

import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackAggregationDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackDetailDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackSummaryDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface REFRecognitionFeedbackRepositoryCustom {

    /**
     * 피드백 상세 조회 (단건).
     * 원본 스냅샷 + 수정 데이터 + 메타 정보를 포함한 전체 DTO.
     */
    Optional<REFFeedbackDetailDto> findDetailByFeedbackId(UUID feedbackId);

    /**
     * 인식 결과 ID 기준 피드백 상세 조회.
     * 클라이언트가 recognitionId로 피드백을 조회하는 주 경로.
     */
    Optional<REFFeedbackDetailDto> findDetailByRecognitionId(UUID recognitionId);

    /**
     * 특정 사용자의 피드백 요약 목록 조회.
     * 선택적으로 상태 필터링 가능 (null이면 전체).
     *
     * @param requesterId 요청자 ID
     * @param statusCode  상태 코드 (nullable, null이면 전체 조회)
     */
    List<REFFeedbackSummaryDto> findSummariesByRequesterId(UUID requesterId, String statusCode);

    /**
     * 제품명 기준 긍정/부정 피드백 집계.
     * Product 자동 등록 판단 및 핸들러 품질 메트릭에 활용.
     *
     * @param productName 정제된 제품명 (orig_product_name)
     */
    Optional<REFFeedbackAggregationDto> countAggregationByProductName(String productName);

    /**
     * 자동 승인 대상 PENDING 피드백 ID 목록 조회.
     * 배치에서 사용 — 생성 후 threshold 시간이 지난 미응답 피드백.
     *
     * @param createdBefore 이 시점 이전에 생성된 PENDING 피드백만
     * @param limit         최대 조회 건수 (배치 사이즈 제한)
     */
    List<REFRecognitionFeedbackId> findPendingIdsCreatedBefore(LocalDateTime createdBefore, int limit);
}