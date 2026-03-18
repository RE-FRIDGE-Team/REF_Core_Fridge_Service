package com.refridge.core_server.product.infra.persistence;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.product.domain.REFProductRepositoryCustom;
import com.refridge.core_server.product.domain.vo.REFProductStatus;
import com.refridge.core_server.product.infra.dto.REFProductSearchResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.refridge.core_server.groceryItem.domain.ar.QREFGroceryItem.rEFGroceryItem;
import static com.refridge.core_server.grocery_category.domain.ar.QREFMajorGroceryCategory.rEFMajorGroceryCategory;
import static com.refridge.core_server.grocery_category.domain.ar.QREFMinorGroceryCategory.rEFMinorGroceryCategory;
import static com.refridge.core_server.product.domain.ar.QREFProduct.rEFProduct;

@Slf4j
@Repository
@RequiredArgsConstructor
public class REFProductRepositoryImpl implements REFProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final String CATEGORY_PATH_SEPARATOR = " > ";

    @Override
    public Optional<REFProductSearchResultDto> searchByProductName(String productName, String brandName) {
        if (productName == null || productName.isBlank()) {
            return Optional.empty();
        }

        // ── 역방향 LIKE: 입력이 DB 제품명을 포함하는 경우 ──
        // ex) 입력 "코카콜라제로레몬" → DB "코카콜라제로" 매칭
        BooleanExpression reverseLike = Expressions.booleanTemplate(
                "{0} LIKE CONCAT('%%', {1}, '%%')",
                Expressions.constant(productName),
                rEFProduct.productName.value
        );

        // ── 정방향 LIKE: DB 제품명이 입력을 포함하는 경우 ──
        BooleanExpression forwardLike = rEFProduct.productName.value.contains(productName);

        // ── CONCAT(brand, ' ', product) 부분 일치 ──
        StringExpression brandProductConcat = Expressions.stringTemplate(
                "CONCAT({0}, ' ', {1})",
                rEFProduct.brandName.value,
                rEFProduct.productName.value
        );

        // ── 브랜드 + 제품명 완전 일치 (브랜드 있을 때만) ──
        BooleanExpression brandExactMatch = (brandName != null && !brandName.isBlank())
                ? rEFProduct.brandName.value.eq(brandName)
                .and(rEFProduct.productName.value.eq(productName))
                : Expressions.FALSE;

        // ── 매칭 우선순위 CASE WHEN ──
        NumberExpression<Integer> matchPriority = new CaseBuilder()
                .when(rEFProduct.productName.value.eq(productName)).then(1)
                .when(brandExactMatch).then(2)
                .when(forwardLike).then(3)
                .when(reverseLike
                        .and(rEFProduct.productName.value.length().goe(2)))  // 너무 짧은 제품명 방지
                .then(4)
                .otherwise(99);

        // ── WHERE: 매칭 가능한 행만 필터 ──
        BooleanExpression matchable = rEFProduct.productName.value.eq(productName)
                .or(brandExactMatch)
                .or(forwardLike)
                .or(brandProductConcat.contains(productName))
                .or(reverseLike.and(rEFProduct.productName.value.length().goe(2)));

        REFProductSearchResultDto result = queryFactory
                .select(Projections.constructor(
                        REFProductSearchResultDto.class,
                        rEFProduct.id,
                        rEFProduct.productName.value,
                        rEFProduct.brandName.value,
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
                .from(rEFProduct)
                .innerJoin(rEFGroceryItem)
                .on(rEFGroceryItem.id.eq(rEFProduct.groceryItemReference.groceryItemId))
                .innerJoin(rEFMajorGroceryCategory)
                .on(rEFMajorGroceryCategory.id.eq(
                        rEFGroceryItem.groceryCategoryReference.majorCategoryId))
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(
                        rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .where(
                        rEFProduct.status.eq(REFProductStatus.ACTIVE),
                        matchable
                )
                .orderBy(
                        matchPriority.asc(),
                        rEFProduct.productName.value.length().asc()
                )
                .fetchFirst();

        return Optional.ofNullable(result);
    }

    /**
     * 제품명 기반 다중 검색 (유사도 순)
     *
     * 사용 케이스: 사용자에게 여러 후보를 보여줄 때
     */
    @Override
    public List<REFProductSearchResultDto> searchMultipleByProductName(String productName, int limit) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }

        return queryFactory
                .select(Projections.constructor(
                        REFProductSearchResultDto.class,
                        rEFProduct.id,
                        rEFProduct.productName.value,
                        rEFProduct.brandName.value,
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
                .from(rEFProduct)
                .innerJoin(rEFGroceryItem)
                .on(rEFGroceryItem.id.eq(rEFProduct.groceryItemReference.groceryItemId))
                .innerJoin(rEFMajorGroceryCategory)
                .on(rEFMajorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.majorCategoryId))
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .where(
                        rEFProduct.productName.value.contains(productName),
                        rEFProduct.status.eq(REFProductStatus.ACTIVE)
                )
                .orderBy(
                        rEFProduct.productName.value.length().asc(),  // 짧은 이름 우선
                        rEFProduct.id.asc()                            // ID 순
                )
                .limit(limit)
                .fetch();
    }
}
