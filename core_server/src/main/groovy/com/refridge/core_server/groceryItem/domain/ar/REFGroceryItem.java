package com.refridge.core_server.groceryItem.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.groceryItem.domain.dto.REFGroceryItemDetailsForFridgeStock;
import com.refridge.core_server.groceryItem.domain.vo.*;
import com.refridge.core_server.groceryItem.infra.REFGroceryItemClassificationConverter;
import com.refridge.core_server.groceryItem.infra.REFGroceryItemStatusConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "ref_grocery_item")
@EntityListeners(AuditingEntityListener.class)
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
    private Set<REFRealProductName> realProductNameSet =  new HashSet<>();

    @Embedded
    /* 엔티티 등록 시간, 엔티티 업데이트 시간 */
    private REFEntityTimeMetaData timeMetaData;

    public REFGroceryItem(String groceryItemName,
                          String representativeImageUrl,
                          String groceryItemClassification) {
        this.groceryItemName = REFGroceryItemName.of(groceryItemName);
        this.representativeImage = REFRepresentativeImage.of(representativeImageUrl);
        this.groceryItemClassification = REFGroceryItemClassification.fromTypeCode(groceryItemClassification);
        this.groceryItemStatus = REFGroceryItemStatus.ACTIVE;
    }

    /* 대표 이미지 변경 */
    public REFRepresentativeImage changeRepresentativeImage(String representativeImageUrl) {
        this.representativeImage = REFRepresentativeImage.of(representativeImageUrl);
        return this.representativeImage;
    }

    /* 식재료명과 매칭되는 상품 삽입 */
    public void addMatchedProduct(REFRealProductName productName) {
        this.realProductNameSet.add(productName);
    }

    /* 식재료명과 매칭되는 상품 삭제 */
    public void removeMatchedProduct(REFRealProductName productName) {
        this.realProductNameSet.remove(productName);
    }

    /* 실제품병 기반으로 매칭된 원재료 관련 정보 획득 */
    public Optional<REFGroceryItemDetailsForFridgeStock> compareToProductAndGetGroceryItemDetailsForFridgeStock(String realProductName){
        return Optional.of(realProductName)
                .filter(productName -> this.realProductNameSet.contains(REFRealProductName.of(productName)))
                .map(matchedProductName -> REFGroceryItemDetailsForFridgeStock.fromDomainVO(
                        this.id, this.groceryItemName, this.representativeImage, this.groceryItemClassification, matchedProductName));

    }

    /* 원재료 DB상 임시 삭제 상태 변경 */
    public void delete(){
        this.groceryItemStatus = REFGroceryItemStatus.DELETED;
    }

}
