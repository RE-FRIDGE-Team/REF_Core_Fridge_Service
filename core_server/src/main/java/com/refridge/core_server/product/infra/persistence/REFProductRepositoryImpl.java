package com.refridge.core_server.product.infra.persistence;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
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

    /**
     * 제품명 기반 검색 (매칭 우선순위 적용)
     *<pre>
     * 매칭 우선순위:
     * 1. 완전 일치 (product_name = ?)
     * 2. 브랜드 + 제품명 일치 (brand + product = ?)
     * 3. 부분 일치 (product_name LIKE %?%)</pre>
     *
     * 실행 쿼리: 최대 3개 (각 우선순위별 1개씩, 매칭 시 조기 종료)
     */
    @Override
    public Optional<REFProductSearchResultDto> searchByProductName(String productName, String brandName) {
        if (productName == null || productName.isBlank()) {
            return Optional.empty();
        }

        // 1순위: 완전 일치
        Optional<REFProductSearchResultDto> exactMatch = searchExactMatch(productName);
        if (exactMatch.isPresent()) {
            log.debug("완전 일치 매칭 성공: {}", productName);
            return exactMatch;
        }

        // 2순위: 브랜드 + 제품명 일치 (브랜드가 있는 경우만)
        if (brandName != null && !brandName.isBlank()) {
            Optional<REFProductSearchResultDto> brandMatch = searchBrandProductMatch(brandName, productName);
            if (brandMatch.isPresent()) {
                log.debug("브랜드+제품명 일치 매칭 성공: {} {}", brandName, productName);
                return brandMatch;
            }
        }

        // 3순위: 부분 일치
        Optional<REFProductSearchResultDto> partialMatch = searchPartialMatch(productName);
        if (partialMatch.isPresent()) {
            log.debug("부분 일치 매칭 성공: {}", productName);
            return partialMatch;
        }

        log.debug("매칭 실패: {}", productName);
        return Optional.empty();
    }

    /**
     * 1순위: 완전 일치 검색
     */
    private Optional<REFProductSearchResultDto> searchExactMatch(String productName) {
        REFProductSearchResultDto result = queryFactory
                .select(Projections.constructor(
                        REFProductSearchResultDto.class,
                        rEFProduct.id,
                        rEFProduct.productName.value,
                        rEFProduct.brandName.value,
                        rEFGroceryItem.id,
                        rEFGroceryItem.groceryItemName.value,
                        // CategoryPath 조합
                        Expressions.stringTemplate(
                                "CONCAT({0}, {1}, {2})",
                                rEFMajorGroceryCategory.categoryName.value,
                                Expressions.constant(CATEGORY_PATH_SEPARATOR),
                                rEFMinorGroceryCategory.categoryName.value
                        ),
                        rEFGroceryItem.representativeImage.presignedUrl
                ))
                .from(rEFProduct)
                // GroceryItem JOIN
                .innerJoin(rEFGroceryItem)
                .on(rEFGroceryItem.id.eq(rEFProduct.groceryItemReference.groceryItemId))
                // Category JOIN
                .innerJoin(rEFMajorGroceryCategory)
                .on(rEFMajorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.majorCategoryId))
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .where(
                        rEFProduct.productName.value.eq(productName),
                        rEFProduct.status.eq(REFProductStatus.ACTIVE)
                )
                .fetchFirst();

        return Optional.ofNullable(result);
    }

    /**
     * 2순위: 브랜드 + 제품명 일치 검색
     */
    private Optional<REFProductSearchResultDto> searchBrandProductMatch(String brandName, String productName) {
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
                .on(rEFMajorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.majorCategoryId))
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .where(
                        rEFProduct.brandName.value.eq(brandName),
                        rEFProduct.productName.value.eq(productName),
                        rEFProduct.status.eq(REFProductStatus.ACTIVE)
                )
                .fetchFirst();

        return Optional.ofNullable(result);
    }

    /**
     * 3순위: 부분 일치 검색 (LIKE 검색)
     *
     * 예: "코카콜라제로" 입력 시 "코카콜라" 매칭
     */
    private Optional<REFProductSearchResultDto> searchPartialMatch(String productName) {
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
                .on(rEFMajorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.majorCategoryId))
                .innerJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.id.eq(rEFGroceryItem.groceryCategoryReference.minorCategoryId))
                .where(
                        rEFProduct.productName.value.contains(productName)
                                .or(Expressions.stringTemplate(
                                        "CONCAT({0}, ' ', {1})",
                                        rEFProduct.brandName.value,
                                        rEFProduct.productName.value
                                ).contains(productName)),
                        rEFProduct.status.eq(REFProductStatus.ACTIVE)
                )
                .orderBy(rEFProduct.productName.value.length().asc())  // 짧은 제품명 우선
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
