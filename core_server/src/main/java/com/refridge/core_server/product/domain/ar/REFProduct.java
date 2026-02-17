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
@Table(name = "ref_grocery_item")
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

    @Embedded
    private REFProductVolume productVolume;

    @Embedded
    private REFProductQuantity productQuantity;

    @Column(name = "product_type", nullable = false, length = 20)
    @Convert(converter = REFProductTypeConverter.class)
    private REFProductType productType;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = REFProductStatusConverter.class)
    private REFProductStatus status;

    @Embedded
    /* 엔티티 등록 시간, 엔티티 업데이트 시간 */
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
}