package com.refridge.core_server.recognition_feedback.application.mapper;

import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackAggregationResult;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackDetailResult;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFFeedbackSummaryResult;
import com.refridge.core_server.recognition_feedback.domain.vo.REFFeedbackStatus;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackAggregationDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackDetailDto;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackSummaryDto;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Infra 레이어 Projection DTO → Application 레이어 Result DTO 변환 매퍼.
 * <p>
 * 상태 코드 → 한글명 변환 등 Application 레이어 관심사를 여기서 처리합니다.
 */
@Component
public class REFFeedbackResultMapper {

    public REFFeedbackDetailResult toDetailResult(REFFeedbackDetailDto dto) {
        REFFeedbackStatus status = REFFeedbackStatus.fromDbCode(dto.statusCode());

        return new REFFeedbackDetailResult(
                dto.feedbackId(),
                dto.recognitionId(),
                dto.statusCode(),
                status.getKorCode(),

                // 원본 스냅샷
                dto.origProductName(),
                dto.origGroceryItemId(),
                dto.origGroceryItemName(),
                dto.origCategoryPath(),
                dto.origBrandName(),
                dto.origQuantity(),
                dto.origVolume(),
                dto.origVolumeUnit(),
                dto.origImageUrl(),
                dto.origCompletedBy(),

                // 수정 데이터
                dto.correctedProductName(),
                dto.correctedGroceryItemName(),
                dto.correctedCategoryPath(),
                dto.correctedBrandName(),
                dto.correctedQuantity(),
                dto.correctedVolume(),
                dto.correctedVolumeUnit(),
                dto.purchasePrice(),

                // diff
                dto.diffProductName(),
                dto.diffGroceryItem(),
                dto.diffCategory(),
                dto.diffBrand(),
                dto.diffQuantityVolume(),

                // 메타
                dto.autoApproved(),
                dto.resolvedAt(),
                dto.createdAt()
        );
    }

    public REFFeedbackSummaryResult toSummaryResult(REFFeedbackSummaryDto dto) {
        REFFeedbackStatus status = REFFeedbackStatus.fromDbCode(dto.statusCode());

        return new REFFeedbackSummaryResult(
                dto.feedbackId(),
                dto.recognitionId(),
                dto.statusCode(),
                status.getKorCode(),
                dto.origProductName(),
                dto.origGroceryItemName(),
                dto.origCategoryPath(),
                dto.origCompletedBy(),
                dto.autoApproved(),
                dto.resolvedAt(),
                dto.createdAt()
        );
    }

    public List<REFFeedbackSummaryResult> toSummaryResultList(List<REFFeedbackSummaryDto> dtos) {
        return dtos.stream().map(this::toSummaryResult).toList();
    }

    public REFFeedbackAggregationResult toAggregationResult(REFFeedbackAggregationDto dto) {
        return new REFFeedbackAggregationResult(
                dto.productName(),
                dto.approvedCount(),
                dto.correctedCount()
        );
    }
}