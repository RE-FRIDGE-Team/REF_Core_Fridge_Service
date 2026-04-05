package com.refridge.core_server.recognition_feedback.domain.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface REFFeedbackReviewItemRepository extends JpaRepository<REFFeedbackReviewItem, Long>, REFFeedbackReviewItemRepositoryCustom {

    /** 유형 + 대상값으로 기존 검수 항목 조회 (중복 방지, 누적 카운트용) */
    Optional<REFFeedbackReviewItem> findByReviewTypeAndTargetValue(
            REFReviewType reviewType, String targetValue);

    /** 상태별 검수 항목 목록 조회 (백오피스용) */
    List<REFFeedbackReviewItem> findByStatusOrderByOccurrenceCountDesc(REFReviewStatus status);

    /** 유형 + 상태별 검수 항목 목록 조회 */
    List<REFFeedbackReviewItem> findByReviewTypeAndStatusOrderByOccurrenceCountDesc(
            REFReviewType reviewType, REFReviewStatus status);

    /** 특정 유형 + 대상값이 PENDING 상태로 이미 존재하는지 */
    @Query("SELECT COUNT(r) > 0 FROM REFFeedbackReviewItem r " +
            "WHERE r.reviewType = :type AND r.targetValue = :value AND r.status = 'P'")
    boolean existsPendingByTypeAndValue(
            @Param("type") REFReviewType type,
            @Param("value") String value);
}