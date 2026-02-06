package com.refridge.core_server.groceryItem.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.groceryItem.domain.dto.REFGroceryItemDetailsForFridgeStock;
import com.refridge.core_server.groceryItem.domain.vo.*;
import com.refridge.core_server.groceryItem.infra.REFGroceryItemClassificationConverter;
import com.refridge.core_server.groceryItem.infra.REFGroceryItemStatusConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Entity
@Builder
@Table(name = "ref_grocery_item")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryItem {

    @Id
    @Column(name = "item_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    /* 식료품 대표 이름 */
    private REFGroceryItemName groceryItemName;

    @Embedded
    /* 대표 이미지 URL */
    private REFRepresentativeImage representativeImage;

    @Column(name = "item_type", nullable = false)
    @Convert(converter = REFGroceryItemClassificationConverter.class)
    /* 식료품 분류 타입 */
    private REFGroceryItemClassification groceryItemClassification;

    @Column(name = "item_status", nullable = false)
    @Convert(converter = REFGroceryItemStatusConverter.class)
    /* 데이터 상태 (활성화, 삭제) */
    private REFGroceryItemStatus groceryItemStatus;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "ref_grocery_item_matched_product", joinColumns = @JoinColumn(name = "item_id"))
    /* 식재료에 매핑된 실제 상품명들 */
    private Set<REFRealProductName> realProductNameSet = new HashSet<>();

    @Embedded
    /* 카테고리 참조 (FK) */
    private REFGroceryCategoryReference groceryCategoryReference;

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

    /* CREATION FACTORY METHOD */
    public static REFGroceryItem create(String groceryItemName,
                                          String representativeImageUrl,
                                          String groceryItemClassification,
                                          Long majorCategoryId, Long minorCategoryId) {
        return REFGroceryItem.builder()
                .groceryItemName(REFGroceryItemName.of(groceryItemName))
                .representativeImage(REFRepresentativeImage.of(representativeImageUrl))
                .groceryItemClassification(REFGroceryItemClassification.fromTypeCode(groceryItemClassification))
                .groceryItemStatus(REFGroceryItemStatus.ACTIVE)
                .groceryCategoryReference(REFGroceryCategoryReference.of(majorCategoryId, minorCategoryId))
                .build();
    }

    /* BUSINESS LOGIC : 식재료의 카테고리를 변경할 수 있다. */
    public void changeCategory(Long majorCategoryId, Long minorCategoryId) {
        if (isActive()) {
            this.groceryCategoryReference = REFGroceryCategoryReference.of(majorCategoryId, minorCategoryId);
        }
    }

    /* BUSINESS LOGIC : 대표 이미지를 변경할 수 있다. */
    public REFRepresentativeImage changeRepresentativeImage(String representativeImageUrl) {
        return this.representativeImage = isActive() ?
                REFRepresentativeImage.of(representativeImageUrl) : this.representativeImage;
    }

    /* BUSINESS LOGIC : 해당 식재료와 매칭되는 상품을 삽입한다. (ex. 곰곰 콩나물 > 콩나물에 소속) */
    public void addMatchedProduct(REFRealProductName productName) {
        if (isActive()) {
            this.realProductNameSet.add(productName);
        }
    }

    /* BUSINESS LOGIC : 해당 식재료와 매칭되는 상품 삭제한다. */
    public void removeMatchedProduct(REFRealProductName productName) {
        if (isActive()) {
            this.realProductNameSet.remove(productName);
        }
    }

    /* BUSINESS LOGIC : 실제품명 기반으로 매칭된 원재료 관련 정보를 획득할 수 있다. */
    public Optional<REFGroceryItemDetailsForFridgeStock> compareToProductAndGetGroceryItemDetailsForFridgeStock(String realProductName) {
        return Optional.ofNullable(realProductName)
                .filter(productName -> this.realProductNameSet.contains(REFRealProductName.of(productName)))
                .map(matchedProductName -> REFGroceryItemDetailsForFridgeStock.fromDomainVO(
                        this.id, this.groceryItemName, this.representativeImage, this.groceryItemClassification, matchedProductName));

    }

    /* INTERNAL LOGIC : 활성화 상태인지 체크 */
    private boolean isActive() {
        return this.groceryItemStatus.equals(REFGroceryItemStatus.ACTIVE);
    }

    /* BUSINESS LOGIC : 원재료를 삭제할 수 있다(활성 -> 삭제 상태 변경) */
    public void delete() {
        this.groceryItemStatus = REFGroceryItemStatus.DELETED;
    }

    /* BUSINESS LOGIC : 삭제된 원재료를 복구할 수 있다(삭제 -> 활성 상태 변경) */
    public void restore() {
        this.groceryItemStatus = REFGroceryItemStatus.ACTIVE;
    }

}
