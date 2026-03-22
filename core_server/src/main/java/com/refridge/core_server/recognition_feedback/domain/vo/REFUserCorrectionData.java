package com.refridge.core_server.recognition_feedback.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 수정한 데이터를 담는 Value Object입니다.
 * <p>
 * 부정 피드백(CORRECTED) 시 사용자가 변경한 값을 보관하며,
 * 긍정 피드백(APPROVED) 시에도 구매 가격만 입력하는 경우 사용됩니다.
 * <p>
 * null 필드는 "해당 항목을 수정하지 않았음"을 의미합니다.
 * {@link REFCorrectionDiff}와 함께 사용되어 정확히 어떤 필드가 변경되었는지 추적합니다.
 */
@Getter
@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFUserCorrectionData {

    @Column(name = "corrected_product_name")
    private String correctedProductName;

    @Column(name = "corrected_grocery_item_name")
    private String correctedGroceryItemName;

    @Column(name = "corrected_category_path")
    private String correctedCategoryPath;

    @Column(name = "corrected_brand_name")
    private String correctedBrandName;

    @Column(name = "corrected_quantity")
    private Integer correctedQuantity;

    @Column(name = "corrected_volume")
    private String correctedVolume;

    @Column(name = "corrected_volume_unit")
    private String correctedVolumeUnit;

    /** 구매 가격 (원 단위) — 승인/수정 모두에서 입력 가능 */
    @Column(name = "purchase_price")
    private Long purchasePrice;

    /**
     * 사용자가 인식 결과를 수정한 경우의 전체 수정 데이터를 생성합니다.
     */
    public static REFUserCorrectionData of(
            String correctedProductName,
            String correctedGroceryItemName,
            String correctedCategoryPath,
            String correctedBrandName,
            Integer correctedQuantity,
            String correctedVolume,
            String correctedVolumeUnit,
            Long purchasePrice
    ) {
        return new REFUserCorrectionData(
                correctedProductName, correctedGroceryItemName,
                correctedCategoryPath, correctedBrandName,
                correctedQuantity, correctedVolume,
                correctedVolumeUnit, purchasePrice
        );
    }

    /**
     * 승인(긍정 피드백) 시 구매 가격만 입력하는 경우.
     * 나머지 필드는 모두 null — 원본 인식 결과와 동일하다는 의미.
     */
    public static REFUserCorrectionData priceOnly(Long purchasePrice) {
        return new REFUserCorrectionData(
                null, null, null, null,
                null, null, null, purchasePrice
        );
    }
}