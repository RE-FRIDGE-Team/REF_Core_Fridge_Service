package com.refridge.core_server.grocery_category.domain;

import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface REFMajorGroceryCategoryRepository extends JpaRepository<REFMajorGroceryCategory, Long> {

    /* 이미 카테고리 중복된 이름이 존재하는지 */
    @Query("SELECT COUNT(m) > 0 FROM REFMajorGroceryCategory m WHERE m.categoryName.value = :categoryName")
    boolean existsByCategoryName(@Param("categoryName") String categoryName);
}