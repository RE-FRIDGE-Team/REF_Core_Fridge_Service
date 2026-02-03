package com.refridge.core_server.groceryItem.infra;

import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemClassification;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFGroceryItemClassificationConverter implements AttributeConverter<REFGroceryItemClassification, String> {

    @Override
    public String convertToDatabaseColumn(REFGroceryItemClassification groceryItemClassification) {
        return groceryItemClassification.getTypeCode();
    }

    @Override
    public REFGroceryItemClassification convertToEntityAttribute(String dbData) {
        return REFGroceryItemClassification.fromTypeCode(dbData);
    }
}
