package com.refridge.core_server.product.domain;

import com.refridge.core_server.product.domain.ar.REFProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface REFProductRepository extends JpaRepository<REFProduct, Long>, REFProductRepositoryCustom {

    /**
     * 제품명과 식재료 ID 조합으로 Product 존재 여부를 확인합니다.
     * 카테고리 재분류 흐름({@code REFProductCategoryUpdateByReassignmentHandler})에서
     * 특정 GroceryItem에 매핑된 Product가 있는지 확인하는 용도로만 사용합니다.
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
     * 카테고리 재분류 흐름에서 비정규화 카테고리 참조를 갱신하는 용도입니다.
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

    /**
     * 제품명으로 ACTIVE 상태의 Product를 조회합니다.
     *
     * <h3>추가 배경 (2026. 4. 14.)</h3>
     * <p>
     * {@code upsertProduct()}에서 제품명 단일 키 기준으로 중복을 체크하기 위해 추가됩니다.
     * 동일 제품명에 GroceryItem이 달라진 경우(식재료 교정)
     * 기존 Product를 업데이트하여 중복 Product 생성을 방지합니다.
     * </p>
     *
     * @param productNameValue 조회할 제품명
     * @return ACTIVE 상태의 Product (없으면 empty)
     */
    @Query("""
            SELECT p FROM REFProduct p
            WHERE p.productName.value = :productNameValue
            AND p.status = com.refridge.core_server.product.domain.vo.REFProductStatus.ACTIVE
            """)
    Optional<REFProduct> findActiveByProductNameValue(
            @Param("productNameValue") String productNameValue
    );
}