package com.refridge.core_server.recognition_feedback.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인식 파이프라인이 산출한 결과의 불변 스냅샷입니다.
 * <p>
 * 피드백 생성 시점에 캡처되며, 이후 수정 여부 판단(diff)의 기준선 역할을 합니다.
 * 인식 결과가 나중에 변경되더라도 피드백의 스냅샷은 변하지 않습니다.
 * <p>
 * {@code completedBy} 필드는 어느 핸들러에서 결과가 도출되었는지 기록하여,
 * 부정 피드백 발생 시 해당 핸들러의 품질 메트릭 집계에 활용됩니다.
 */
@Getter
@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFOriginalRecognitionSnapshot {

    @Column(name = "orig_product_name")
    private String productName;

    @Column(name = "orig_grocery_item_id")
    private Long groceryItemId;

    @Column(name = "orig_grocery_item_name")
    private String groceryItemName;

    @Column(name = "orig_category_path")
    private String categoryPath;

    @Column(name = "orig_brand_name")
    private String brandName;

    @Column(name = "orig_quantity")
    private Integer quantity;

    @Column(name = "orig_volume")
    private String volume;

    @Column(name = "orig_volume_unit")
    private String volumeUnit;

    @Column(name = "orig_image_url")
    private String imageUrl;

    /** 어느 핸들러에서 인식이 완료되었는지 (ExclusionFilter, GroceryItemDictMatch, ...) */
    @Column(name = "orig_completed_by")
    private String completedBy;

    /** 비식재료 필터에 의해 반려되었는지 */
    @Column(name = "orig_rejected")
    private boolean rejected;

    /** 반려 시 매칭된 비식재료 키워드 (반려가 아니면 null) */
    @Column(name = "orig_rejection_keyword")
    private String rejectionKeyword;

    public static REFOriginalRecognitionSnapshot of(
            String productName,
            Long groceryItemId,
            String groceryItemName,
            String categoryPath,
            String brandName,
            Integer quantity,
            String volume,
            String volumeUnit,
            String imageUrl,
            String completedBy,
            boolean rejected,
            String rejectionKeyword
    ) {
        return new REFOriginalRecognitionSnapshot(
                productName, groceryItemId, groceryItemName,
                categoryPath, brandName, quantity, volume,
                volumeUnit, imageUrl, completedBy,
                rejected, rejectionKeyword
        );
    }
}