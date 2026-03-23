package com.refridge.core_server.recognition_feedback.infra.persistence.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepositoryCustom;
import com.refridge.core_server.recognition_feedback.domain.vo.REFFeedbackStatus;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackAggregationDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackDetailDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.refridge.core_server.recognition_feedback.domain.ar.QREFRecognitionFeedback.rEFRecognitionFeedback;

@Repository
@RequiredArgsConstructor
public class REFRecognitionFeedbackRepositoryImpl implements REFRecognitionFeedbackRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 피드백 상세 조회 (단건, feedbackId 기준).
     * 실행 쿼리: 1개 — Embedded 필드 전체를 단일 SELECT로 조회.
     */
    @Override
    public Optional<REFFeedbackDetailDto> findDetailByFeedbackId(UUID feedbackId) {
        if (feedbackId == null) return Optional.empty();

        REFFeedbackDetailDto result = queryFactory
                .select(detailProjection())
                .from(rEFRecognitionFeedback)
                .where(rEFRecognitionFeedback.id.value.eq(feedbackId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 피드백 상세 조회 (단건, recognitionId 기준).
     * 실행 쿼리: 1개 — 유니크 제약으로 최대 1건 보장.
     */
    @Override
    public Optional<REFFeedbackDetailDto> findDetailByRecognitionId(UUID recognitionId) {
        if (recognitionId == null) return Optional.empty();

        REFFeedbackDetailDto result = queryFactory
                .select(detailProjection())
                .from(rEFRecognitionFeedback)
                .where(rEFRecognitionFeedback.recognitionReference.recognitionId.eq(recognitionId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 사용자별 피드백 요약 목록.
     * 실행 쿼리: 1개 — 상태 필터 optional.
     */
    @Override
    public List<REFFeedbackSummaryDto> findSummariesByRequesterId(UUID requesterId, String statusCode) {
        if (requesterId == null) return List.of();

        return queryFactory
                .select(Projections.constructor(
                        REFFeedbackSummaryDto.class,
                        rEFRecognitionFeedback.id.value,
                        rEFRecognitionFeedback.recognitionReference.recognitionId,
                        rEFRecognitionFeedback.status.stringValue(),
                        rEFRecognitionFeedback.originalSnapshot.productName,
                        rEFRecognitionFeedback.originalSnapshot.groceryItemName,
                        rEFRecognitionFeedback.originalSnapshot.categoryPath,
                        rEFRecognitionFeedback.originalSnapshot.completedBy,
                        rEFRecognitionFeedback.autoApproved,
                        rEFRecognitionFeedback.resolvedAt,
                        rEFRecognitionFeedback.timeMetaData.createdAt
                ))
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.requesterReference.requesterId.eq(requesterId),
                        statusCondition(statusCode)
                )
                .orderBy(rEFRecognitionFeedback.timeMetaData.createdAt.desc())
                .fetch();
    }

    /**
     * 제품명 기준 긍정/부정 피드백 집계.
     * 실행 쿼리: 1개 — CASE WHEN + GROUP BY로 단일 쿼리 집계.
     * <p>
     * PENDING 상태는 아직 확정되지 않았으므로 집계에서 제외합니다.
     */
    @Override
    public Optional<REFFeedbackAggregationDto> countAggregationByProductName(String productName) {
        if (productName == null || productName.isBlank()) return Optional.empty();

        REFFeedbackAggregationDto result = queryFactory
                .select(Projections.constructor(
                        REFFeedbackAggregationDto.class,
                        rEFRecognitionFeedback.originalSnapshot.productName,
                        new CaseBuilder()
                                .when(rEFRecognitionFeedback.status.eq(REFFeedbackStatus.APPROVED))
                                .then(1L).otherwise(0L).sum(),
                        new CaseBuilder()
                                .when(rEFRecognitionFeedback.status.eq(REFFeedbackStatus.CORRECTED))
                                .then(1L).otherwise(0L).sum()
                ))
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.originalSnapshot.productName.eq(productName),
                        rEFRecognitionFeedback.status.ne(REFFeedbackStatus.PENDING)
                )
                .groupBy(rEFRecognitionFeedback.originalSnapshot.productName)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 자동 승인 대상 PENDING 피드백 ID 목록.
     * 실행 쿼리: 1개 — 배치에서 순회하며 autoApprove() 호출.
     *
     * @param createdBefore 이 시점 이전에 생성된 PENDING 피드백
     * @param limit         배치 사이즈 제한
     */
    @Override
    public List<REFRecognitionFeedbackId> findPendingIdsCreatedBefore(LocalDateTime createdBefore, int limit) {
        List<UUID> rawIds = queryFactory
                .select(rEFRecognitionFeedback.id.value)
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.PENDING),
                        rEFRecognitionFeedback.timeMetaData.createdAt.lt(createdBefore)
                )
                .orderBy(rEFRecognitionFeedback.timeMetaData.createdAt.asc())
                .limit(limit)
                .fetch();

        return rawIds.stream()
                .map(REFRecognitionFeedbackId::of)
                .toList();
    }

    /* ──────────────────── Projection 빌더 ──────────────────── */

    /**
     * 상세 조회용 Projection.
     * 원본 스냅샷 + 수정 데이터 + diff + 메타 전체 컬럼.
     */
    private com.querydsl.core.types.ConstructorExpression<REFFeedbackDetailDto> detailProjection() {
        return Projections.constructor(
                REFFeedbackDetailDto.class,
                // 식별자
                rEFRecognitionFeedback.id.value,
                rEFRecognitionFeedback.recognitionReference.recognitionId,
                rEFRecognitionFeedback.requesterReference.requesterId,
                rEFRecognitionFeedback.status.stringValue(),

                // 원본 스냅샷
                rEFRecognitionFeedback.originalSnapshot.productName,
                rEFRecognitionFeedback.originalSnapshot.groceryItemId,
                rEFRecognitionFeedback.originalSnapshot.groceryItemName,
                rEFRecognitionFeedback.originalSnapshot.categoryPath,
                rEFRecognitionFeedback.originalSnapshot.brandName,
                rEFRecognitionFeedback.originalSnapshot.quantity,
                rEFRecognitionFeedback.originalSnapshot.volume,
                rEFRecognitionFeedback.originalSnapshot.volumeUnit,
                rEFRecognitionFeedback.originalSnapshot.imageUrl,
                rEFRecognitionFeedback.originalSnapshot.completedBy,

                // 사용자 수정
                rEFRecognitionFeedback.userCorrection.correctedProductName,
                rEFRecognitionFeedback.userCorrection.correctedGroceryItemName,
                rEFRecognitionFeedback.userCorrection.correctedCategoryPath,
                rEFRecognitionFeedback.userCorrection.correctedBrandName,
                rEFRecognitionFeedback.userCorrection.correctedQuantity,
                rEFRecognitionFeedback.userCorrection.correctedVolume,
                rEFRecognitionFeedback.userCorrection.correctedVolumeUnit,
                rEFRecognitionFeedback.userCorrection.purchasePrice,

                // diff
                rEFRecognitionFeedback.correctionDiff.productNameChanged,
                rEFRecognitionFeedback.correctionDiff.groceryItemChanged,
                rEFRecognitionFeedback.correctionDiff.categoryChanged,
                rEFRecognitionFeedback.correctionDiff.brandChanged,
                rEFRecognitionFeedback.correctionDiff.quantityOrVolumeChanged,

                // 메타
                rEFRecognitionFeedback.autoApproved,
                rEFRecognitionFeedback.resolvedAt,
                rEFRecognitionFeedback.timeMetaData.createdAt
        );
    }

    /* ──────────────────── 조건 빌더 ──────────────────── */

    private BooleanExpression statusCondition(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) return null;
        REFFeedbackStatus status = REFFeedbackStatus.fromDbCode(statusCode);
        return rEFRecognitionFeedback.status.eq(status);
    }
}