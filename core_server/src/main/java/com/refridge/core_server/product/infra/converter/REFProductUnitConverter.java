package com.refridge.core_server.product.infra.converter;

import com.refridge.core_server.product.domain.vo.REFProductUnit;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * REFProductUnit JPA Converter
 *
 * DB 저장: code (단일 문자)
 * - "ml" → "ml"
 * - "kg" → "kg"
 * - null → null
 */
@Converter
public class REFProductUnitConverter implements AttributeConverter<REFProductUnit, String> {

    @Override
    public String convertToDatabaseColumn(REFProductUnit unit) {
        if (unit == null) {
            return null;
        }
        return unit.getSymbol();
    }

    @Override
    public REFProductUnit convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        return REFProductUnit.fromSymbol(dbData)
                .orElseThrow(() -> new IllegalStateException(
                        "알 수 없는 용량 단위입니다: " + dbData
                ));
    }
}