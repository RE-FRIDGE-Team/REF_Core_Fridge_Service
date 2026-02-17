package com.refridge.core_server.product.infra.converter;

import com.refridge.core_server.product.domain.vo.REFProductQuantityUnit;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFProductQuantityUnitConverter implements AttributeConverter<REFProductQuantityUnit, String> {

    @Override
    public String convertToDatabaseColumn(REFProductQuantityUnit unit) {
        if (unit == null) {
            return null;
        }
        return unit.getSymbol();
    }

    @Override
    public REFProductQuantityUnit convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        return REFProductQuantityUnit.fromSymbol(dbData)
                .orElseThrow(() -> new IllegalStateException(
                        "알 수 없는 수량 단위입니다: " + dbData
                ));
    }
}