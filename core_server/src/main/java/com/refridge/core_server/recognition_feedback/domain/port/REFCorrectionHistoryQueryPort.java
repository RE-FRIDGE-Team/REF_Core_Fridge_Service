package com.refridge.core_server.recognition_feedback.domain.port;

import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackCorrectionHistoryDto;

import java.util.List;

/**
 * 피드백 BC가 외부(인식 BC 등)에 수정 이력 데이터를 제공하기 위한 포트입니다.
 * <p>
 * 인식 BC의 Application Service가 recognize() 응답에
 * "타 사용자 수정 추천"을 포함시킬 때 이 포트를 통해 조회합니다.
 * <p>
 * 피드백 BC의 인프라 레이어에서 구현됩니다.
 */
public interface REFCorrectionHistoryQueryPort {

    /**
     * 동일 원본 제품명에 대한 타 사용자 수정 이력을 조회합니다.
     * <p>
     * CORRECTED 상태인 피드백에서 수정 조합을 GROUP BY하여
     * 빈도 높은 순으로 반환합니다.
     *
     * @param originalProductName 원본 정제 제품명
     * @param limit               최대 반환 건수
     * @return 수정 이력 목록 (빈도 높은 순, 없으면 빈 리스트)
     */
    List<REFFeedbackCorrectionHistoryDto> findByProductName(String originalProductName, int limit);
}