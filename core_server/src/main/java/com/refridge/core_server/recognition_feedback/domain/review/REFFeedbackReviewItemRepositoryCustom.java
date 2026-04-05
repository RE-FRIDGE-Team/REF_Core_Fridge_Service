package com.refridge.core_server.recognition_feedback.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 검수 항목 커스텀 쿼리 인터페이스.
 * <p>
 * 관리자 대시보드 조회 및 Spring Batch 처리를 위한 특수 쿼리를 제공합니다.
 * {@link REFFeedbackReviewItemRepository}가 이 인터페이스를 상속하여
 * QueryDSL 구현체({@code REFFeedbackReviewItemRepositoryImpl})와 연결됩니다.
 */
public interface REFFeedbackReviewItemRepositoryCustom {

    /**
     * 상태와 유형으로 필터링한 검수 항목을 발생 횟수 내림차순으로 페이징 조회합니다.
     * <p>
     * 관리자 대시보드의 검수 큐 조회에 사용됩니다.
     * 동점이면 생성일 오름차순(오래된 순)으로 처리합니다.
     *
     * @param status   검수 상태 (null이면 전체 상태 조회)
     * @param type     검수 유형 (null이면 전체 유형 조회)
     * @param pageable 페이징 정보
     * @return 검수 항목 페이지
     */
    Page<REFFeedbackReviewItem> findByStatusAndType(
            REFReviewStatus status, REFReviewType type, Pageable pageable);

    /**
     * ML 학습 대기({@link REFReviewStatus#ML_TRAINING_PENDING}) 항목을 조회합니다.
     * <p>
     * Phase 2에서 구현할 Spring Batch {@code REFMLTrainingExportJob}의 Reader와
     * 관리자 CSV 내보내기 요청 시 사용됩니다.
     *
     * @param limit 최대 조회 건수
     * @return ML 학습 대기 항목 목록 (생성일 오름차순)
     */
    List<REFFeedbackReviewItem> findMlTrainingPendingItems(int limit);

    /**
     * 터미널 상태(APPROVED/REJECTED)인 항목의 ID 목록을 조회합니다.
     * <p>
     * Phase 2에서 구현할 Spring Batch 일괄 삭제 Step의 Reader에서 사용됩니다.
     * 처리 완료된 항목이 누적되어 테이블이 비대해지는 것을 방지합니다.
     *
     * @param limit 최대 조회 건수
     * @return 삭제 대상 항목 ID 목록 (생성일 오름차순)
     */
    List<Long> findTerminalItemIdsForCleanup(int limit);

    /**
     * 특정 상태의 항목 수를 조회합니다.
     * <p>
     * 관리자 대시보드 배지(badge) 카운트 표시에 사용됩니다.
     *
     * @param status 조회할 검수 상태
     * @return 해당 상태의 항목 수
     */
    long countByStatus(REFReviewStatus status);
}
