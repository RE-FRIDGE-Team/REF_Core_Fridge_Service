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
                        rEFRecognitionFeedback.userCorrection.correctedProductName.isNotNull()
                )
                .groupBy(
                        rEFRecognitionFeedback.originalSnapshot.productName,
                        rEFRecognitionFeedback.userCorrection.correctedProductName
                )
                .orderBy(rEFRecognitionFeedback.id.value.count().desc())
                .fetch();
    }

    @Override
    public List<REFFeedbackGroceryItemMappingCountDto> findGroceryItemMappingCountsByOriginalName(
            String originalGroceryItemName) {
        if (originalGroceryItemName == null || originalGroceryItemName.isBlank()) return List.of();
        return queryFactory
                .select(Projections.constructor(
                        REFFeedbackGroceryItemMappingCountDto.class,
                        rEFRecognitionFeedback.originalSnapshot.groceryItemName,
                        rEFRecognitionFeedback.userCorrection.correctedGroceryItemName,
                        rEFRecognitionFeedback.id.value.count()
                ))
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.originalSnapshot.groceryItemName
                                .eq(originalGroceryItemName),
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.CORRECTED),
                        rEFRecognitionFeedback.userCorrection.correctedGroceryItemName.isNotNull()
                )
                .groupBy(
                        rEFRecognitionFeedback.originalSnapshot.groceryItemName,
                        rEFRecognitionFeedback.userCorrection.correctedGroceryItemName
                )
                .orderBy(rEFRecognitionFeedback.id.value.count().desc())
                .fetch();
    }

    /**
     * 특정 원본 제품명에 대해 APPROVED 상태인 피드백 수를 조회합니다.
     *
     * <p>
     * Redis alias 후보 Hash의 {@code __total__} 복원 시 CORRECTED 합계에 더해
     * Gate 2 비율이 과대 계산되는 문제를 방지합니다.
     * </p>
     *
     * 실행 쿼리: 1개
     * <pre>
     * SELECT COUNT(*) FROM ref_recognition_feedback
     * WHERE orig_product_name = :originalProductName
     * AND status = 'A'
     * </pre>
     */
    @Override
    public long countApprovedByOriginalProductName(String originalProductName) {
        if (originalProductName == null || originalProductName.isBlank()) return 0L;
        Long result = queryFactory
                .select(rEFRecognitionFeedback.id.value.count())
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.originalSnapshot.productName.eq(originalProductName),
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.APPROVED)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    /**
     * 특정 원본 식재료명에 대해 APPROVED 상태인 피드백 수를 조회합니다.
     *
     * <p>
     * Redis grocery-item-correction 후보 Hash의 {@code __total__} 복원 시 CORRECTED 합계에 더해
     * Gate 2 비율이 과대 계산되는 문제를 방지합니다.
     * </p>
     *
     * 실행 쿼리: 1개
     * <pre>
     * SELECT COUNT(*) FROM ref_recognition_feedback
     * WHERE orig_grocery_item_name = :originalGroceryItemName
     * AND status = 'A'
     * </pre>
     */
    @Override
    public long countApprovedByOriginalGroceryItemName(String originalGroceryItemName) {
        if (originalGroceryItemName == null || originalGroceryItemName.isBlank()) return 0L;
        Long result = queryFactory
                .select(rEFRecognitionFeedback.id.value.count())
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.originalSnapshot.groceryItemName
                                .eq(originalGroceryItemName),
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.APPROVED)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    /**
     * 특정 원본 브랜드에 대해 사용자가 교체 입력한 브랜드별 집계를 반환합니다.
     * <p>
     * CORRECTED 상태이면서 {@code orig_brand_name}이 일치하고
     * {@code corrected_brand_name}이 null이 아닌 피드백을
     * {@code corrected_brand_name} 기준으로 GROUP BY하여 횟수 내림차순 반환합니다.
     * </p>
     *
     * 실행 쿼리: 1개
     * <pre>
     * SELECT orig_brand_name, corrected_brand_name, COUNT(*) AS selectionCount
     * FROM ref_recognition_feedback
     * WHERE orig_brand_name = :originalBrandName
     *   AND status = 'C'
     *   AND corrected_brand_name IS NOT NULL
     * GROUP BY orig_brand_name, corrected_brand_name
     * ORDER BY selectionCount DESC
     * </pre>
     */
    @Override
    public List<REFFeedbackBrandCorrectionCountByOriginalDto> findBrandCorrectionCountsByOriginalBrand(
            String originalBrandName) {
        if (originalBrandName == null || originalBrandName.isBlank()) return List.of();
        return queryFactory
                .select(Projections.constructor(
                        REFFeedbackBrandCorrectionCountByOriginalDto.class,
                        rEFRecognitionFeedback.originalSnapshot.brandName,
                        rEFRecognitionFeedback.userCorrection.correctedBrandName,
                        rEFRecognitionFeedback.id.value.count()
                ))
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.originalSnapshot.brandName.eq(originalBrandName),
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.CORRECTED),
                        rEFRecognitionFeedback.userCorrection.correctedBrandName.isNotNull()
                )
                .groupBy(
                        rEFRecognitionFeedback.originalSnapshot.brandName,
                        rEFRecognitionFeedback.userCorrection.correctedBrandName
                )
                .orderBy(rEFRecognitionFeedback.id.value.count().desc())
                .fetch();
    }

    /**
     * 특정 원본 브랜드명에 대해 APPROVED 상태인 피드백 수를 조회합니다.
     * <p>
     * 트랙 2 Redis miss 복원 시 {@code __total__}을 정확히 재구성하기 위해
     * CORRECTED 합계와 합산합니다. APPROVED가 누락되면 Gate 2 비율이 과대 계산됩니다.
     * </p>
     *
     * 실행 쿼리: 1개
     * <pre>
     * SELECT COUNT(*) FROM ref_recognition_feedback
     * WHERE orig_brand_name = :originalBrandName
     * AND status = 'A'
     * </pre>
     */
    @Override
    public long countApprovedByOriginalBrandName(String originalBrandName) {
        if (originalBrandName == null || originalBrandName.isBlank()) return 0L;
        Long result = queryFactory
                .select(rEFRecognitionFeedback.id.value.count())
                .from(rEFRecognitionFeedback)
                .where(
                        rEFRecognitionFeedback.originalSnapshot.brandName.eq(originalBrandName),
                        rEFRecognitionFeedback.status.eq(REFFeedbackStatus.APPROVED)
                )
                .fetchOne();
        return result != null ? result : 0L;
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