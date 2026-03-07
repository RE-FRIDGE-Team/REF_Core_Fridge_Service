package com.refridge.core_server.product.domain;


import com.refridge.core_server.product.domain.ar.REFProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface REFProductRepository extends JpaRepository<REFProduct, Long>, REFProductRepositoryCustom {

    @Query("""
            SELECT COUNT(p) > 0
            FROM REFProduct p
            WHERE p.productName.value = :productNameValue
            AND p.groceryItemReference.groceryItemId = :groceryItemId
            """)
    boolean existsByProductNameValueAndGroceryItemId(
            @Param("productNameValue") String productNameValue,
            @Param("groceryItemId") Long groceryItemId
    );
}
