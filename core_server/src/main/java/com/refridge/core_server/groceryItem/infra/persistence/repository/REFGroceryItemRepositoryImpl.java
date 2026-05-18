package com.refridge.core_server.groceryItem.infra.persistence.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepositoryCustom;
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemStatus;
import com.refridge.core_server.groceryItem.infra.persistence.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.refridge.core_server.groceryItem.domain.ar.QREFGroceryItem.rEFGroceryItem;
import static com.refridge.core_server.grocery_category.domain.ar.QREFMajorGroceryCategory.rEFMajorGroceryCategory;
import static com.refridge.core_server.grocery_category.domain.ar.QREFMinorGroceryCategory.rEFMinorGroceryCategory;

@Repository
@RequiredArgsConstructor
public class REFGroceryItemRepositoryImpl implements REFGroceryItemRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    private static final String CATEGORY_PATH_SEPARATOR = " > ";

    /**
     * 단건 요약 DTO 조회
     * 실행 쿼리: 1개 (단일 SELECT)
     * - GroceryItem 테이블에서 필요한 컬럼만 조회
     * - @Embedded 필드는 JOIN 없이 같은 row에서 조회
     * - ElementCollection(realProductNameSet)은 조회하지 않음
     */
    @Override
    public Optional<REFGroceryItemSummarizedDTO> findSummarizedDTOById(Long id) {
        REFGroceryItemSummarizedDTO result = jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemSummarizedDTO.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        rEFGroceryItem.representativeImage.presignedUrl
                ))
                .from(rEFGroceryItem)
                .where(rEFGroceryItem.id.in(id),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE))
                .fetchOne();

        return Optional.ofNullable(result);
    }


    /**
     * 다건 요약 DTO 조회 (Batching)
     * 실행 쿼리: 1개 (IN 절 사용)
     * - ids 개수가 1000개를 초과하면 경고 로그 (DB별 IN 절 제약 고려)
     * - 단일 SELECT로 모든 데이터 조회
     *
     * @param ids 조회할 GroceryItem ID 목록
     * @return 조회된 DTO 리스트 (순서 보장 안됨)
     */
    @Override
    public List<REFGroceryItemSummarizedDTO> findAllSummarizedDTOsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemSummarizedDTO.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        rEFGroceryItem.representativeImage.presignedUrl
                ))
                .from(rEFGroceryItem)
                .where(rEFGroceryItem.id.in(ids),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE))
                .fetch();
    }

    /**
     * 단건 상세 DTO 조회
     * 실행 쿼리: 1개
     * - CategoryFullName은 ID로만 구성 (실제 이름은 Service Layer에서 처리 권장)
     * - Enum은 stringValue()로 코드값 조회
     */
    @Override
    public Optional<REFGroceryItemDetailDTO> findDetailDTOById(Long id) {
        REFGroceryItemDetailDTO result = jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemDetailDTO.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        rEFGroceryItem.groceryCategoryReference.majorCategoryId,
                        rEFGroceryItem.groceryCategoryReference.minorCategoryId,
                        rEFGroceryItem.representativeImage.presignedUrl,
                        rEFGroceryItem.groceryItemClassification.stringValue()
                ))
                .from(rEFGroceryItem)
                .where(rEFGroceryItem.id.in(id),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 다건 상세 DTO 조회 (Batch)
     * 실행 쿼리: 1개
     * N+1 방지: IN 절로 단일 쿼리 실행
     */
    @Override
    public List<REFGroceryItemDetailDTO> findAllDetailDTOsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemDetailDTO.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        rEFGroceryItem.groceryCategoryReference.majorCategoryId,
                        rEFGroceryItem.groceryCategoryReference.minorCategoryId,
                        rEFGroceryItem.representativeImage.presignedUrl,
                        rEFGroceryItem.groceryItemClassification.stringValue()
                ))
                .from(rEFGroceryItem)
                .where(rEFGroceryItem.id.in(ids),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE))
                .fetch();
    }

    /**
     * 식재료 이름으로 GroceryItem 조회 (카테고리 경로 포함).
     * 실행 쿼리: 1개 (JOIN 2회)
     */
    @Override
    public Optional<REFGroceryItemWithCategoryPathDto> findByGroceryItemName(String groceryItemName) {
        if (groceryItemName == null || groceryItemName.isBlank()) {
            return Optional.empty();
        }

        REFGroceryItemWithCategoryPathDto result = jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemWithCategoryPathDto.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        Expressions.stringTemplate(
                                "CONCAT({0}, {1}, {2})",
                                rEFMajorGroceryCategory.categoryName.value,
                                Expressions.constant(CATEGORY_PATH_SEPARATOR),
                                rEFMinorGroceryCategory.categoryName.value
                        ),
                        rEFGroceryItem.representativeImage.presignedUrl
                ))
                .from(rEFGroceryItem)
                .innerJoin(rEFMajorGroceryCategory)
                .on(rEFMajorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.majorCategoryId))
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .where(
                        rEFGroceryItem.groceryItemName.value.eq(groceryItemName),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<REFGroceryItemForUpsertDto> findForUpsertByGroceryItemName(String groceryItemName) {
        if (groceryItemName == null || groceryItemName.isBlank()) {
            return Optional.empty();
        }

        REFGroceryItemForUpsertDto result = jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemForUpsertDto.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryCategoryReference.majorCategoryId,
                        rEFGroceryItem.groceryCategoryReference.minorCategoryId
                ))
                .from(rEFGroceryItem)
                .where(
                        rEFGroceryItem.groceryItemName.value.eq(groceryItemName),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<REFGroceryItemItemTypeDto> findItemTypesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemItemTypeDto.class,
                        rEFGroceryItem.id,
                        rEFMinorGroceryCategory.itemType
                ))
                .from(rEFGroceryItem)
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(
                        rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .where(
                        rEFGroceryItem.id.in(ids),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE)
                )
                .fetch();
    }

    /**
     * fridge_server 노출용 메타데이터 단건 조회.
     * <p>
     * GroceryItem 본 테이블 + MinorCategory JOIN 1회로 itemType까지 가져옵니다.
     * usageMetadata.* 필드는 @Embedded이므로 같은 row에 존재 → 추가 JOIN 없음.
     * </p>
     */
    @Override
    public Optional<REFGroceryItemMetadataDTO> findMetadataDTOById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        REFGroceryItemMetadataDTO result = jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemMetadataDTO.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        rEFMinorGroceryCategory.itemType,
                        rEFGroceryItem.usageMetadata.defaultUnit.stringValue(),
                        rEFGroceryItem.usageMetadata.minPortionAmount,
                        rEFGroceryItem.usageMetadata.maxPortionAmount,
                        rEFGroceryItem.usageMetadata.spoonVolumeMl,
                        rEFGroceryItem.usageMetadata.pieceWeightGram,
                        rEFGroceryItem.usageMetadata.allowedUsageTypesCsv,
                        rEFGroceryItem.usageMetadata.defaultUsageType.stringValue()
                ))
                .from(rEFGroceryItem)
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(
                        rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .where(
                        rEFGroceryItem.id.eq(id),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 부트스트랩용 — 커서 기반 ID 페이징 조회.
     * id 오름차순으로 afterId 이후 ACTIVE 항목들의 ID만 가져옵니다.
     */
    @Override
    public List<Long> findActiveIdsAfter(Long afterId, int pageSize) {
        Long cursor = (afterId == null) ? 0L : afterId;
        int size = Math.max(1, pageSize);

        return jpaQueryFactory
                .select(rEFGroceryItem.id)
                .from(rEFGroceryItem)
                .where(
                        rEFGroceryItem.id.gt(cursor),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE)
                )
                .orderBy(rEFGroceryItem.id.asc())
                .limit(size)
                .fetch();
    }
}