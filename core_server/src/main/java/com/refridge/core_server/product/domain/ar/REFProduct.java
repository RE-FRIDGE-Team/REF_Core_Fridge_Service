package com.refridge.core_server.product.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.product.domain.vo.*;
import com.refridge.core_server.product.infra.converter.REFProductStatusConverter;
import com.refridge.core_server.product.infra.converter.REFProductTypeConverter;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

@Entity
@SuppressWarnings("NullableProblems")
@Builder(access = AccessLevel.PROTECTED)
@Table(name = "ref_product", indexes = {
        @Index(name = "idx_product_name_status",
                columnList = "product_name, status"),
        @Index(name = "idx_product_brand_name_status",
                columnList = "brand_name, product_name, status"),
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

    @Getter
    @Embedded
    private REFGroceryItemReference groceryItemReference;

    @Column(name = "is_virtual", nullable = false)
    private boolean isVirtual;

    @Embedded
    private REFEntityTimeMetaData timeMetaData;

    @PrePersist
    protected void onCreate() {
        if (timeMetaData == null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = new REFEntityTimeMetaData(now, now);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (timeMetaData != null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = timeMetaData.updateModifiedAt(now);
        }
    }

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
     * 카테고리 재분류 승인 흐름에서 사용됩니다.
     */
    public void updateCategoryReference(Long newMajorCategoryId, Long newMinorCategoryId) {
        this.groceryItemReference = REFGroceryItemReference.of(
                this.groceryItemReference.getGroceryItemId(),
                newMajorCategoryId,
                newMinorCategoryId
        );
    }

    /**
     * 식재료 교정으로 인해 Product의 GroceryItem 매핑 전체를 갱신합니다.
     *
     * <h3>추가 배경 (2026. 4. 14.)</h3>
     * <p>
     * 기존 {@link #updateCategoryReference}는 같은 GroceryItem 내에서
     * 카테고리만 바뀌는 케이스를 처리합니다.
     * 이 메서드는 GroceryItem 자체가 바뀌는 식재료 교정 케이스를 처리합니다.
     * {@link com.refridge.core_server.product.application.REFProductLifeCycleService#upsertProduct}에서
     * 동일 제품명에 GroceryItem이 달라진 경우 호출됩니다.
     * </p>
     *
     * @param newGroceryItemId   교정된 GroceryItem ID
     * @param newMajorCategoryId 교정된 GroceryItem의 대분류 카테고리 ID
     * @param newMinorCategoryId 교정된 GroceryItem의 중분류 카테고리 ID
     */
    public void updateGroceryItemReference(Long newGroceryItemId,
                                           Long newMajorCategoryId,
                                           Long newMinorCategoryId) {
        this.groceryItemReference = REFGroceryItemReference.of(
                newGroceryItemId,
                newMajorCategoryId,
                newMinorCategoryId
        );
    }
}