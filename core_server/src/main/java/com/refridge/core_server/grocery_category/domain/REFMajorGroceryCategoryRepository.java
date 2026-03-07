package com.refridge.core_server.grocery_category.domain;

import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface REFMajorGroceryCategoryRepository extends JpaRepository<REFMajorGroceryCategory, Long>, REFMajorGroceryCategoryRepositoryCustom {

    /* 이미 카테고리 중복된 이름이 존재하는지 */
    @Query("SELECT COUNT(m) > 0 FROM REFMajorGroceryCategory m WHERE m.categoryName.value = :categoryName")
    boolean existsByCategoryName(@Param("categoryName") String categoryName);

    /* 대분류 이름으로 단건 조회 - Initializer 정합성 체크용 */
    @Query("SELECT m FROM REFMajorGroceryCategory m WHERE m.categoryName.value = :categoryName")
    Optional<REFMajorGroceryCategory> findByName(@Param("categoryName") String categoryName);

    /* 정합성 체크용 - DB에 존재하는 모든 대분류 이름을 Set으로 반환 */
    @Query("SELECT m.categoryName.value FROM REFMajorGroceryCategory m")
    Set<String> findAllCategoryNames();

    @Query("SELECT m.id FROM REFMajorGroceryCategory m WHERE m.categoryName.value = :categoryName")
    Optional<Long> findCategoryIdByName(@Param("categoryName") String categoryName);
}