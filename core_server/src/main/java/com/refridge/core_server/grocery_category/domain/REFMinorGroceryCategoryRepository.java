package com.refridge.core_server.grocery_category.domain;

import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface REFMinorGroceryCategoryRepository extends JpaRepository<REFMinorGroceryCategory, Long> {
}
