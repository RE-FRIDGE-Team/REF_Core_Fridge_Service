package com.refridge.core_server.recognition_feedback.infra.converter;

import com.refridge.core_server.recognition_feedback.domain.review.REFReviewStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFReviewStatusConverter implements AttributeConverter<REFReviewStatus, String> {

    @Override
    public String convertToDatabaseColumn(REFReviewStatus attribute) {
        return attribute.getDbCode();
    }

    @Override
    public REFReviewStatus convertToEntityAttribute(String dbData) {
        return REFReviewStatus.fromDbCode(dbData);
    }
}
