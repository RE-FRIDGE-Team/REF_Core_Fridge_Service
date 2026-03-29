package com.refridge.core_server.recognition_feedback.infra.converter;

import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFReviewTypeConverter implements AttributeConverter<REFReviewType, String> {

    @Override
    public String convertToDatabaseColumn(REFReviewType attribute) {
        return attribute.getDbCode();
    }

    @Override
    public REFReviewType convertToEntityAttribute(String dbData) {
        return REFReviewType.fromDbCode(dbData);
    }
}