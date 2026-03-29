package com.refridge.core_server.recognition_feedback.application.dto.command;

import com.refridge.core_server.recognition_feedback.domain.vo.REFUserCorrectionData;
import lombok.Builder;

import java.util.UUID;

/**
 * 사용자가 인식 결과를 수정할 때 사용하는 커맨드.
 * <p>
 * null 필드는 "해당 항목을 수정하지 않음"을 의미합니다.
 *
 * @param recognitionId 인식 결과 ID (피드백이 없으면 Lazy Creation)
 */
@Builder
public record REFFeedbackCorrectCommand(
        UUID recognitionId,
        String correctedProductName,
        String correctedGroceryItemName,
        String correctedCategoryPath,
        String correctedBrandName,
        Integer correctedQuantity,
        String correctedVolume,
        String correctedVolumeUnit,
        Long purchasePrice
) {

    public REFUserCorrectionData toCorrectionData() {
        return REFUserCorrectionData.of(
                correctedProductName, correctedGroceryItemName,
                correctedCategoryPath, correctedBrandName,
                correctedQuantity, correctedVolume,
                correctedVolumeUnit, purchasePrice
        );
    }
}