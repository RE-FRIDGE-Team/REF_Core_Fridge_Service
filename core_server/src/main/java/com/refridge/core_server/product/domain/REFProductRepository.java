package com.refridge.core_server.product.domain;

import com.refridge.core_server.product.domain.ar.REFProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface REFProductRepository extends JpaRepository<REFProduct, Long>, REFProductRepositoryCustom {

    /**
     * 제품명과 식재료 ID 조합으로 Product 존재 여부를 확인합니다.
     * <p>
     * upsert 중복 방지에 사용됩니다.
     */
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

    /**
     * 제품명과 식재료 ID로 ACTIVE 상태의 Product를 조회합니다.
     * <p>
     * 기존 Product의 비정규화 카테고리 참조를 갱신하기 위해 사용합니다.
     *
     * @param productName   정제된 제품명
     * @param groceryItemId 연관 식재료 ID
     * @return ACTIVE 상태 Product (없으면 empty)
     */
    @Query("""
            SELECT p FROM REFProduct p
            WHERE p.productName.value = :productName
            AND p.groceryItemReference.groceryItemId = :groceryItemId
            AND p.status = com.refridge.core_server.product.domain.vo.REFProductStatus.ACTIVE
            """)
    Optional<REFProduct> findByProductNameAndGroceryItemId(
            @Param("productName") String productName,
            @Param("groceryItemId") Long groceryItemId
    );
}
