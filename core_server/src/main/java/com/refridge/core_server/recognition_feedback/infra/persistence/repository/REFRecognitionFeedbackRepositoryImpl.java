package com.refridge.core_server.recognition_feedback.infra.persistence.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepositoryCustom;
import com.refridge.core_server.recognition_feedback.domain.vo.REFFeedbackStatus;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.*;
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

    @Override
    public List<REFRecognitionFeedbackId> findPendingIdsCreatedBefore(
            LocalDateTime createdBefore, int limit) {
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
        return rawIds.stream().map(REFRecognitionFeedbackId::of).toList();
    }

    @Override
    public List<REFFeedbackCorrectionHistoryDto> findCorrectionHistoryByProductName(
            String originalProductName, int limit) {
        if (originalProductName == null || originalProductName.isBlank()) return List.of();
        return queryFactory
                .select(Projections.constructor(
                        REFFeedbackCorrectionHistoryDto.class,
                        rEFRecognitionFeedback.userCorrection.correctedProductName,
                        rEFRecognitionFeedback.userCorrection.correctedGroceryItemName,
                        rEFRecognitionFeedback.userCorrection.correctedBrandName,
                        rEFRecognitionFeedback.userCorrection.correctedCategoryPath,
                        rEFRecognitionFeedback.id.value.count()
                ))
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.originalSnapshot.productName.eq(originalProductName),
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.CORRECTED)
                )
                .groupBy(
                        rEFRecognitionFeedback.userCorrection.correctedProductName,
                        rEFRecognitionFeedback.userCorrection.correctedGroceryItemName,
                        rEFRecognitionFeedback.userCorrection.correctedBrandName,
                        rEFRecognitionFeedback.userCorrection.correctedCategoryPath
                )
                .orderBy(rEFRecognitionFeedback.id.value.count().desc())
                .limit(limit)
                .fetch();
    }

    /**
     * 특정 브랜드명으로 수정된 CORRECTED 피드백 수를 조회합니다.
     * 실행 쿼리: 1개
     *
     * SELECT COUNT(*) FROM ref_recognition_feedback
     * WHERE corrected_brand_name = :correctedBrandName
     * AND status = 'C'
     */
    @Override
    public long countByCorrectBrandName(String correctedBrandName) {
        if (correctedBrandName == null || correctedBrandName.isBlank()) return 0L;
        Long result = queryFactory
                .select(rEFRecognitionFeedback.id.value.count())
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.userCorrection.correctedBrandName
                                .eq(correctedBrandName),
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.CORRECTED)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    /**
     * 원본 제품명 기준으로 수정 제품명별 선택 횟수를 집계합니다.
     * 실행 쿼리: 1개 (GROUP BY + COUNT)
     *
     * SELECT orig_product_name, corrected_product_name, COUNT(*) as selection_count
     * FROM ref_recognition_feedback
     * WHERE orig_product_name = :originalProductName
     * AND status = 'C'
     * GROUP BY orig_product_name, corrected_product_name
     * ORDER BY selection_count DESC
     */
    @Override
    public List<REFFeedbackBrandCorrectionCountDto> findAliasCandidateCountsByOriginalName(
            String originalProductName) {
        if (originalProductName == null || originalProductName.isBlank()) return List.of();
        return queryFactory
                .select(Projections.constructor(
                        REFFeedbackBrandCorrectionCountDto.class,
                        rEFRecognitionFeedback.originalSnapshot.productName,
                        rEFRecognitionFeedback.userCorrection.correctedProductName,
                        rEFRecognitionFeedback.id.value.count()
                ))
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.originalSnapshot.productName.eq(originalProductName),
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.CORRECTED),
                        // correctedProductName이 null인 행 제외 (제품명 변경 없는 피드백)
                        rEFRecognitionFeedback.userCorrection.correctedProductName.isNotNull()
                )
                .groupBy(
                        rEFRecognitionFeedback.originalSnapshot.productName,
                        rEFRecognitionFeedback.userCorrection.correctedProductName
                )
                .orderBy(rEFRecognitionFeedback.id.value.count().desc())
                .fetch();
    }

    /* ──────────────────── Projection 빌더 ──────────────────── */

    private com.querydsl.core.types.ConstructorExpression<REFFeedbackDetailDto> detailProjection() {
        return Projections.constructor(
                REFFeedbackDetailDto.class,
                rEFRecognitionFeedback.id.value,
                rEFRecognitionFeedback.recognitionReference.recognitionId,
                rEFRecognitionFeedback.requesterReference.requesterId,
                rEFRecognitionFeedback.status.stringValue(),
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
                rEFRecognitionFeedback.userCorrection.correctedProductName,
                rEFRecognitionFeedback.userCorrection.correctedGroceryItemName,
                rEFRecognitionFeedback.userCorrection.correctedCategoryPath,
                rEFRecognitionFeedback.userCorrection.correctedBrandName,
                rEFRecognitionFeedback.userCorrection.correctedQuantity,
                rEFRecognitionFeedback.userCorrection.correctedVolume,
                rEFRecognitionFeedback.userCorrection.correctedVolumeUnit,
                rEFRecognitionFeedback.userCorrection.purchasePrice,
                rEFRecognitionFeedback.correctionDiff.productNameChanged,
                rEFRecognitionFeedback.correctionDiff.groceryItemChanged,
                rEFRecognitionFeedback.correctionDiff.categoryChanged,
                rEFRecognitionFeedback.correctionDiff.brandChanged,
                rEFRecognitionFeedback.correctionDiff.quantityOrVolumeChanged,
                rEFRecognitionFeedback.autoApproved,
                rEFRecognitionFeedback.resolvedAt,
                rEFRecognitionFeedback.timeMetaData.createdAt
        );
    }

    private BooleanExpression statusCondition(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) return null;
        REFFeedbackStatus status = REFFeedbackStatus.fromDbCode(statusCode);
        return rEFRecognitionFeedback.status.eq(status);
    }
}