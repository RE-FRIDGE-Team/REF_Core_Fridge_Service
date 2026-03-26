package com.refridge.core_server.recognition_feedback.application.dto.command;

import com.refridge.core_server.recognition_feedback.domain.vo.REFUserCorrectionData;
import lombok.Builder;

import java.util.UUID;

/**
 * 사용자가 인식 결과를 수정할 때 사용하는 커맨드.
 * <p>
 * null 필드는 "해당 항목을 수정하지 않음"을 의미합니다.
 * AR이 실질적 변경 여부를 판단하여
 * 실제로 correct()를 호출할지 approve()로 전환할지 결정합니다.
 */
@Builder
public record REFFeedbackCorrectCommand(
        UUID feedbackId,
        String correctedProductName,
        String correctedGroceryItemName,
        String correctedCategoryPath,
        String correctedBrandName,
        Integer correctedQuantity,
        String correctedVolume,
        String correctedVolumeUnit,
        Long purchasePrice
) {

    /**
     * Command → Domain VO 변환.
     * Application Service에서 Domain Service 호출 전에 사용합니다.
     */
    public REFUserCorrectionData toCorrectionData() {
        return REFUserCorrectionData.of(
                correctedProductName, correctedGroceryItemName,
                correctedCategoryPath, correctedBrandName,
                correctedQuantity, correctedVolume,
                correctedVolumeUnit, purchasePrice
        );
    }
}