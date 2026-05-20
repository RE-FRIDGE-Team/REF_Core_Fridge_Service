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

    @Override
    public List<REFGroceryItemSummarizedDTO> findAllSummarizedDTOsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
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

    @Override
    public List<REFGroceryItemDetailDTO> findAllDetailDTOsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
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

    @Override
    public Optional<REFGroceryItemWithCategoryPathDto> findByGroceryItemName(String groceryItemName) {
        if (groceryItemName == null || groceryItemName.isBlank()) return Optional.empty();

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
        if (groceryItemName == null || groceryItemName.isBlank()) return Optional.empty();

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
        if (ids == null || ids.isEmpty()) return List.of();
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
     * @Embedded usageMetadata.* 필드는 같은 row에 존재 → JOIN 1회만 필요 (MinorCategory의 itemType).
     * </p>
     */
    @Override
    public Optional<REFGroceryItemMetadataDTO> findMetadataDTOById(Long id) {
        if (id == null) return Optional.empty();

        REFGroceryItemMetadataDTO result = jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemMetadataDTO.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        rEFMinorGroceryCategory.itemType,
                        rEFGroceryItem.usageMetadata.defaultUnit.stringValue(),
                        rEFGroceryItem.usageMetadata.minPortionAmount,
                        rEFGroceryItem.usageMetadata.maxPortionAmount,
                        rEFGroceryItem.usageMetadata.pieceWeightGram,
                        rEFGroceryItem.usageMetadata.allowedUsageUnitsCsv,
                        rEFGroceryItem.usageMetadata.defaultUsageUnit.stringValue()
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

    /**
     * 부트스트랩용 — id/이름/minor·major categoryName 한 번에 조회.
     * <p>
     * GroceryItem + MinorCategory + MajorCategory JOIN 1쿼리.
     * Spring의 SDR 페이징보다 커서 기반이 5000건 단위 안정성에 유리.
     * </p>
     */
    @Override
    public List<REFGroceryItemBootstrapDTO> findBootstrapPageAfter(Long afterId, int pageSize) {
        Long cursor = (afterId == null) ? 0L : afterId;
        int size = Math.max(1, pageSize);

        return jpaQueryFactory
                .select(Projections.constructor(
                        REFGroceryItemBootstrapDTO.class,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        rEFMinorGroceryCategory.categoryName.value,
                        rEFMajorGroceryCategory.categoryName.value
                ))
                .from(rEFGroceryItem)
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(
                        rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .innerJoin(rEFMajorGroceryCategory)
                .on(rEFMajorGroceryCategory.id.eq(
                        rEFGroceryItem.groceryCategoryReference.majorCategoryId))
                .where(
                        rEFGroceryItem.id.gt(cursor),
                        rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE)
                )
                .orderBy(rEFGroceryItem.id.asc())
                .limit(size)
                .fetch();
    }
}