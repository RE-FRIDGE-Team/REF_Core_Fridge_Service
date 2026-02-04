package com.refridge.core_server.grocery_category.domain;

import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface REFGroceryCategoryRepository extends JpaRepository<REFMajorGroceryCategory, Long> {

}