package com.refridge.core_server.grocery_category.infra.persistence.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemStatus;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepositoryCustom;
import com.refridge.core_server.grocery_category.infra.persistence.dto.QREFCategoryMetaDataWithCountRowDto;
import com.refridge.core_server.grocery_category.infra.persistence.dto.REFCategoryMetaDataWithCountRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.refridge.core_server.groceryItem.domain.ar.QREFGroceryItem.rEFGroceryItem;
import static com.refridge.core_server.grocery_category.domain.ar.QREFMajorGroceryCategory.rEFMajorGroceryCategory;
import static com.refridge.core_server.grocery_category.domain.ar.QREFMinorGroceryCategory.rEFMinorGroceryCategory;

@Repository
@RequiredArgsConstructor
public class REFMajorGroceryCategoryRepositoryImpl implements REFMajorGroceryCategoryRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<REFCategoryMetaDataWithCountRowDto> findAllCategoryHierarchyWithItemCount() {
        // 중분류 기준 LEFT JOIN → ACTIVE 아이템 count
        return jpaQueryFactory
                .select(new QREFCategoryMetaDataWithCountRowDto(
                        rEFMajorGroceryCategory.id,
                        rEFMajorGroceryCategory.categoryName.value,
                        rEFMinorGroceryCategory.id,
                        rEFMinorGroceryCategory.categoryName.value,
                        rEFGroceryItem.id.count()
                ))
                .from(rEFMajorGroceryCategory)
                .leftJoin(rEFMinorGroceryCategory)
                .on(rEFMinorGroceryCategory.majorCategory.eq(rEFMajorGroceryCategory))
                .leftJoin(rEFGroceryItem)
                .on(rEFGroceryItem.groceryCategoryReference.minorCategoryId.eq(rEFMinorGroceryCategory.id)
                        .and(rEFGroceryItem.groceryItemStatus.eq(REFGroceryItemStatus.ACTIVE)))
                .groupBy(
                        rEFMajorGroceryCategory.id,
                        rEFMajorGroceryCategory.categoryName.value,
                        rEFMinorGroceryCategory.id,
                        rEFMinorGroceryCategory.categoryName.value
                )
                .orderBy(
                        rEFMajorGroceryCategory.id.asc(),
                        rEFMinorGroceryCategory.id.asc()
                )
                .fetch();
    }

}
