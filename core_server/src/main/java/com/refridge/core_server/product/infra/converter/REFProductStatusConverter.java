package com.refridge.core_server.product.infra.converter;

import com.refridge.core_server.product.domain.vo.REFProductStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFProductStatusConverter implements AttributeConverter<REFProductStatus, String> {

    @Override
    public String convertToDatabaseColumn(REFProductStatus attribute) {
        return attribute.getCode();
    }

    @Override
    public REFProductStatus convertToEntityAttribute(String dbData) {
        return REFProductStatus.fromCode(dbData);
    }
}
