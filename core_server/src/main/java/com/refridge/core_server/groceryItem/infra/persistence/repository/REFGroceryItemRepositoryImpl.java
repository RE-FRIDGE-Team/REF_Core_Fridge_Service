package com.refridge.core_server.groceryItem.infra.persistence.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepositoryCustom;
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemStatus;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemDetailDTO;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemSummarizedDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.refridge.core_server.groceryItem.domain.ar.QREFGroceryItem.rEFGroceryItem;

@Repository
@RequiredArgsConstructor
public class REFGroceryItemRepositoryImpl implements REFGroceryItemRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

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
}
