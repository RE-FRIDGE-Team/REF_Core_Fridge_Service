package com.refridge.core_server.recognition_feedback.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 원본 인식 결과와 사용자 수정 데이터 간의 차이를 나타내는 Value Object입니다.
 * <p>
 * {@link REFOriginalRecognitionSnapshot}과 {@link REFUserCorrectionData}를 비교하여
 * 자동 계산되며, 부정 피드백 이벤트 핸들러가 어떤 개선 액션을 수행할지 결정하는 데 사용됩니다.
 * <p>
 * 예시:<pre>
 *   - brandChanged == true → 브랜드 사전 추가 후보
 *   - productNameChanged == true → alias 매핑 사전 등록 후보
 *   - groceryItemChanged == true → GroceryItem 매핑 재학습
 * </pre>
 */
@Getter
@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFCorrectionDiff {

    @Column(name = "diff_product_name")
    private boolean productNameChanged;

    @Column(name = "diff_grocery_item")
    private boolean groceryItemChanged;

    @Column(name = "diff_category")
    private boolean categoryChanged;

    @Column(name = "diff_brand")
    private boolean brandChanged;

    @Column(name = "diff_quantity_volume")
    private boolean quantityOrVolumeChanged;

    /**
     * 원본 스냅샷과 사용자 수정 데이터를 비교하여 diff를 계산합니다.
     * <p>
     * 수정 데이터의 필드가 null이면 "변경하지 않음"으로 간주합니다.
     * 즉, null은 "원본과 동일" 입니다.
     */
    public static REFCorrectionDiff calculate(
            REFOriginalRecognitionSnapshot original,
            REFUserCorrectionData correction) {

        return new REFCorrectionDiff(
                isChanged(original.getProductName(), correction.getCorrectedProductName()),
                isChanged(original.getGroceryItemName(), correction.getCorrectedGroceryItemName()),
                isChanged(original.getCategoryPath(), correction.getCorrectedCategoryPath()),
                isChanged(original.getBrandName(), correction.getCorrectedBrandName()),
                isQuantityOrVolumeChanged(original, correction)
        );
    }

    /**
     * 변경된 필드 유형을 Set으로 반환합니다.
     * 이벤트 핸들러에서 switch/분기 처리에 활용됩니다.
     */
    public Set<REFCorrectionType> getChangedFields() {
        Set<REFCorrectionType> changed = EnumSet.noneOf(REFCorrectionType.class);

        if (productNameChanged)       changed.add(REFCorrectionType.PRODUCT_NAME);
        if (groceryItemChanged)       changed.add(REFCorrectionType.GROCERY_ITEM);
        if (categoryChanged)          changed.add(REFCorrectionType.CATEGORY);
        if (brandChanged)             changed.add(REFCorrectionType.BRAND);
        if (quantityOrVolumeChanged)  changed.add(REFCorrectionType.QUANTITY_VOLUME);

        return changed;
    }

    /**
     * 변경된 필드가 하나도 없는지 확인합니다.
     * 가격만 입력한 경우 등에서 true를 반환할 수 있습니다.
     */
    public boolean hasNoChanges() {
        return !productNameChanged && !groceryItemChanged
                && !categoryChanged && !brandChanged
                && !quantityOrVolumeChanged;
    }

    /* ---- Internal ---- */

    /**
     * correctedValue가 null이면 "수정하지 않음" → 변경 없음.
     * correctedValue가 non-null이면 originalValue와 비교.
     */
    private static boolean isChanged(String originalValue, String correctedValue) {
        if (correctedValue == null) return false;
        return !Objects.equals(originalValue, correctedValue);
    }

    private static boolean isQuantityOrVolumeChanged(
            REFOriginalRecognitionSnapshot original,
            REFUserCorrectionData correction) {

        boolean quantityChanged = correction.getCorrectedQuantity() != null
                && !Objects.equals(original.getQuantity(), correction.getCorrectedQuantity());

        boolean volumeChanged = correction.getCorrectedVolume() != null
                && !Objects.equals(original.getVolume(), correction.getCorrectedVolume());

        return quantityChanged || volumeChanged;
    }
}