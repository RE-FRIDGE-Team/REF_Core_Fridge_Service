package com.refridge.core_server.product_recognition.infra.converter;

import com.refridge.core_server.product_recognition.domain.vo.REFEntrySource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFEntrySourceConverter implements AttributeConverter<REFEntrySource, String> {

    @Override
    public String convertToDatabaseColumn(REFEntrySource attribute) {
        return attribute.getDbCode();
    }

    @Override
    public REFEntrySource convertToEntityAttribute(String dbData) {
        return REFEntrySource.fromDbCode(dbData);
    }
}
