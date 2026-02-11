package com.refridge.core_server.product_recognition.infra;

import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFRecognitionDictionaryTypeConverter implements AttributeConverter<REFRecognitionDictionaryType, String> {

    @Override
    public String convertToDatabaseColumn(REFRecognitionDictionaryType attribute) {
        return attribute.getDbCode();
    }

    @Override
    public REFRecognitionDictionaryType convertToEntityAttribute(String dbData) {
        return REFRecognitionDictionaryType.fromDbCode(dbData);
    }
}
