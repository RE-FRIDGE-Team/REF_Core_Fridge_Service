package com.refridge.core_server.grocery_category.domain;

import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface REFMinorGroceryCategoryRepository extends JpaRepository<REFMinorGroceryCategory, Long>, REFMinorGroceryCategoryRepositoryCustom {

    /* 특정 대분류 하위의 중분류 이름 Set 조회 - Initializer 누락 중분류 체크용 */
    @Query("SELECT m.categoryName.value FROM REFMinorGroceryCategory m WHERE m.majorCategory.id = :majorId")
    Set<String> findMinorCategoryNamesByMajorId(@Param("majorId") Long majorId);

    @Query("SELECT m.id FROM REFMinorGroceryCategory m WHERE m.categoryName.value = :categoryName")
    Optional<Long> findCategoryIdByName(@Param("categoryName") String categoryName);
}
