package com.refridge.core_server.recognition_feedback.application;

import com.refridge.core_server.recognition_feedback.application.dto.result.REFReviewItemResult;
import com.refridge.core_server.recognition_feedback.domain.review.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 검수 큐 조회 전용 Application Service입니다.
 *
 * <h3>정렬 기준</h3>
 * <p>
 * 동일 유형+대상값에 대해 복수 피드백이 누적될수록 {@code occurrenceCount}가 증가합니다.
 * 빈도가 높을수록 실제 문제일 가능성이 높으므로 {@code occurrenceCount} 내림차순을 우선 정렬 기준으로 삼고,
 * 동점이면 {@code createdAt} 오름차순(오래된 순)으로 처리합니다.
 * </p>
 *
 * <h3>필터링 옵션</h3>
 * <ul>
 *   <li>{@code statusCode} — null이면 전체 상태 조회</li>
 *   <li>{@code typeCode} — null이면 전체 유형 조회</li>
 *   <li>ML 학습 대기 항목만 조회: statusCode = "MT" 전달</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFReviewAdminService
 */
@Service
@RequiredArgsConstructor
public class REFReviewItemQueryService {

    private final REFFeedbackReviewItemRepository reviewItemRepository;

    /**
     * 검수 항목을 상태/유형으로 필터링하여 페이징 조회합니다.
     *
     * @param statusCode 상태 코드 (null이면 전체, e.g. "P", "A", "R", "MT")
     * @param typeCode   유형 코드 (null이면 전체, e.g. "EX", "CA", "GI")
     * @param pageable   페이징 정보
     * @return 검수 항목 페이지
     */
    @Transactional(readOnly = true)
    public Page<REFReviewItemResult> getReviewItems(
            String statusCode, String typeCode, Pageable pageable) {

        REFReviewStatus status = (statusCode != null && !statusCode.isBlank())
                ? REFReviewStatus.fromDbCode(statusCode) : null;
        REFReviewType type = (typeCode != null && !typeCode.isBlank())
                ? REFReviewType.fromDbCode(typeCode) : null;

        return reviewItemRepository
                .findByStatusAndType(status, type, pageable)
                .map(this::toResult);
    }

    /**
     * 특정 검수 항목 단건을 조회합니다.
     *
     * @param reviewId 검수 항목 ID
     * @return 검수 항목 결과
     * @throws IllegalArgumentException 항목이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public REFReviewItemResult getReviewItem(Long reviewId) {
        return reviewItemRepository.findById(reviewId)
                .map(this::toResult)
                .orElseThrow(() -> new IllegalArgumentException(
                        "검수 항목을 찾을 수 없습니다: reviewId=" + reviewId));
    }

    /**
     * ML 학습 대기 항목 건수를 조회합니다 (관리자 대시보드 배지용).
     *
     * @return {@link REFReviewStatus#ML_TRAINING_PENDING} 상태 항목 수
     */
    @Transactional(readOnly = true)
    public long getMlTrainingPendingCount() {
        return reviewItemRepository.countByStatus(REFReviewStatus.ML_TRAINING_PENDING);
    }

    /**
     * PENDING 상태 항목 건수를 조회합니다 (관리자 대시보드 배지용).
     *
     * @return {@link REFReviewStatus#PENDING} 상태 항목 수
     */
    @Transactional(readOnly = true)
    public long getPendingCount() {
        return reviewItemRepository.countByStatus(REFReviewStatus.PENDING);
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    private REFReviewItemResult toResult(REFFeedbackReviewItem item) {
        return new REFReviewItemResult(
                item.getId(),
                item.getReviewType().getDbCode(),
                item.getReviewType().getDescription(),
                item.getTargetValue(),
                item.getContextDetail(),
                item.getSourceHandlerName(),
                item.getOccurrenceCount(),
                item.getStatus().getDbCode(),
                item.getStatus().getKorCode(),
                item.getAdminNote(),
                item.getResolvedAt(),
                item.getTimeMetaData().getCreatedAt()
        );
    }
}
