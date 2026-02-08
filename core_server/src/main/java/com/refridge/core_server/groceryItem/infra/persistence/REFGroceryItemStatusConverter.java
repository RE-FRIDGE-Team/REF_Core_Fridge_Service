package com.refridge.core_server.groceryItem.infra.persistence;

import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class REFGroceryItemStatusConverter implements AttributeConverter<REFGroceryItemStatus, String> {

    @Override
    public String convertToDatabaseColumn(REFGroceryItemStatus refGroceryItemStatus) {
        return refGroceryItemStatus.getStatusCode();
    }

    @Override
    public REFGroceryItemStatus convertToEntityAttribute(String dbData) {
        return REFGroceryItemStatus.fromStatusCode(dbData);
    }
}
