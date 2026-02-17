package com.refridge.core_server.product.infra.converter;

import com.refridge.core_server.product.domain.vo.REFProductType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFProductTypeConverter implements AttributeConverter<REFProductType, String> {

    @Override
    public String convertToDatabaseColumn(REFProductType attribute) {
        return attribute.getCode();
    }

    @Override
    public REFProductType convertToEntityAttribute(String dbData) {
        return REFProductType.fromCode(dbData);
    }
}
