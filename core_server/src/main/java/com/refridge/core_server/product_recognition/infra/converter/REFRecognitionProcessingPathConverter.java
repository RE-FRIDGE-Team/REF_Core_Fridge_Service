package com.refridge.core_server.product_recognition.infra.converter;

import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionProcessingPath;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFRecognitionProcessingPathConverter implements AttributeConverter<REFRecognitionProcessingPath, String> {

    @Override
    public String convertToDatabaseColumn(REFRecognitionProcessingPath attribute) {
        return attribute.getDbCode();
    }

    @Override
    public REFRecognitionProcessingPath convertToEntityAttribute(String dbData) {
        return REFRecognitionProcessingPath.fromDbCode(dbData);
    }
}
