package com.refridge.core_server.product_recognition.infra.converter;

import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFProductRecognitionStatusConverter implements AttributeConverter<REFProductRecognitionStatus, String> {

    @Override
    public String convertToDatabaseColumn(REFProductRecognitionStatus attribute) {
        return attribute.getDbCode();
    }

    @Override
    public REFProductRecognitionStatus convertToEntityAttribute(String dbData) {
        return REFProductRecognitionStatus.convertFromDbCode(dbData);
    }
}
