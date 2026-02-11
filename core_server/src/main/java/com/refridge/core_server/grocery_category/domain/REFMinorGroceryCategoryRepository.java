package com.refridge.core_server.grocery_category.domain;

import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface REFMinorGroceryCategoryRepository extends JpaRepository<REFMinorGroceryCategory, Long>, REFMinorGroceryCategoryRepositoryCustom {
}
