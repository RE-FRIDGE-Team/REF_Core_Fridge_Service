package com.refridge.core_server.recognition_feedback.domain;

import com.refridge.core_server.recognition_feedback.domain.ar.REFRecognitionFeedback;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface REFRecognitionFeedbackRepository
        extends JpaRepository<REFRecognitionFeedback, REFRecognitionFeedbackId>,
        REFRecognitionFeedbackRepositoryCustom {

    /** 인식 결과 ID로 피드백 단건 조회 (1:1 유니크) */
    @Query("SELECT f FROM REFRecognitionFeedback f WHERE f.recognitionReference.recognitionId = :recognitionId")
    Optional<REFRecognitionFeedback> findByRecognitionId(@Param("recognitionId") UUID recognitionId);

    /** 인식 결과 ID로 피드백 존재 여부 확인 — createPending 중복 방지용 */
    @Query("SELECT COUNT(f) > 0 FROM REFRecognitionFeedback f WHERE f.recognitionReference.recognitionId = :recognitionId")
    boolean existsByRecognitionId(@Param("recognitionId") UUID recognitionId);
}