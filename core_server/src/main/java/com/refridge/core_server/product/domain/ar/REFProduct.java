package com.refridge.core_server.product.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.product.domain.vo.*;
import com.refridge.core_server.product.infra.converter.REFProductStatusConverter;
import com.refridge.core_server.product.infra.converter.REFProductTypeConverter;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

/**
 * 제품 집계 루트.
 * <pre>
 * 제품의 책임:
 * - 실제 유통되는 제품명 색인 (제품명 검색용)
 * - GroceryItem(식재료) 매핑 (분류용)
 * - 비정규화된 카테고리 참조 보관 ({@link REFGroceryItemReference})
 * </pre>
 *
 * <h3>카테고리 참조 비정규화</h3>
 * <p>
 * {@link REFGroceryItemReference}는 {@code groceryItemId}와 함께
 * {@code majorCategoryId}, {@code minorCategoryId}를 비정규화하여 보유합니다.
 * GroceryItem의 카테고리가 변경되면 {@link #updateCategoryReference}를 통해
 * 이 참조를 명시적으로 동기화해야 합니다.
 * </p>
 */
@Entity
@SuppressWarnings("NullableProblems")
@Builder(access = AccessLevel.PROTECTED)
@Table(name = "ref_product", indexes = {
        // 완전 일치 + 상태 필터
        @Index(name = "idx_product_name_status",
                columnList = "product_name, status"),
        // 브랜드 + 제품명 복합
        @Index(name = "idx_product_brand_name_status",
                columnList = "brand_name, product_name, status"),
        // FK JOIN 최적화
        @Index(name = "idx_product_grocery_item_id",
                columnList = "grocery_item_id")
})
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFProduct extends AbstractAggregateRoot<REFProduct> {

    @Id
    @Getter
    @Column(name = "item_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(
            name = "product_seq",
            sequenceName = "ref_product_seq",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    /**
     * 정제된 제품명을 담고 있습니다.<pre>
     * ex) "서울우유 멸균우유 200ml" -> "멸균우유"
     * ex) "CJ 햇반 210g" -> "햇반"</pre>
     */
    @Embedded
    private REFProductName productName;

    @Embedded
    private REFBrandName brandName;

    @Column(name = "product_type", nullable = false, length = 20)
    @Convert(converter = REFProductTypeConverter.class)
    private REFProductType productType;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = REFProductStatusConverter.class)
    private REFProductStatus status;

    @Embedded
    private REFGroceryItemReference groceryItemReference;

    @Column(name = "is_virtual", nullable = false)
    private boolean isVirtual;

    @Embedded
    private REFEntityTimeMetaData timeMetaData;

    /* JPA 생성 시점 콜백 - createdAt 자동 업데이트 */
    @PrePersist
    protected void onCreate() {
        if (timeMetaData == null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = new REFEntityTimeMetaData(now, now);
        }
    }

    /* JPA 수정 시점 콜백 - updatedAt 자동 업데이트 */
    @PreUpdate
    protected void onUpdate() {
        if (timeMetaData != null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = timeMetaData.updateModifiedAt(now);
        }
    }

    /**
     * CSV 부트스트랩용 팩토리 메서드.
     * productType: 일단 전부 GENERIC으로 적재, 추후 배치 보정 예정
     */
    public static REFProduct create(
            String productName,
            String brandName,
            Long groceryItemId,
            Long majorCategoryId,
            Long minorCategoryId
    ) {
        return REFProduct.builder()
                .productName(REFProductName.of(productName))
                .brandName(REFBrandName.of(brandName))
                .groceryItemReference(REFGroceryItemReference.of(
                        groceryItemId, majorCategoryId, minorCategoryId))
                .productType(REFProductType.GENERIC)
                .status(REFProductStatus.ACTIVE)
                .isVirtual(false)
                .build();
    }

    /**
     * GroceryItem 카테고리 변경 시 Product의 비정규화된 카테고리 참조를 갱신합니다.
     * <p>
     * {@link REFGroceryItemReference}는 {@code majorCategoryId}와 {@code minorCategoryId}를
     * 비정규화하여 보유합니다. 관리자가 카테고리 재분류를 승인하면
     * GroceryItem과 Product의 카테고리 정보를 일관되게 유지합니다.
     *
     * @param newMajorCategoryId 새 대분류 카테고리 ID
     * @param newMinorCategoryId 새 중분류 카테고리 ID
     */
    public void updateCategoryReference(Long newMajorCategoryId, Long newMinorCategoryId) {
        this.groceryItemReference = REFGroceryItemReference.of(
                this.groceryItemReference.getGroceryItemId(),
                newMajorCategoryId,
                newMinorCategoryId
        );
    }
}
