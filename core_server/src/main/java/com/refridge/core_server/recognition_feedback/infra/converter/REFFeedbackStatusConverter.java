package com.refridge.core_server.recognition_feedback.infra.converter;

import com.refridge.core_server.recognition_feedback.domain.vo.REFFeedbackStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFFeedbackStatusConverter implements AttributeConverter<REFFeedbackStatus, String> {

    @Override
    public String convertToDatabaseColumn(REFFeedbackStatus attribute) {
        return attribute.getDbCode();
    }

    @Override
    public REFFeedbackStatus convertToEntityAttribute(String dbData) {
        return REFFeedbackStatus.fromDbCode(dbData);
    }
}