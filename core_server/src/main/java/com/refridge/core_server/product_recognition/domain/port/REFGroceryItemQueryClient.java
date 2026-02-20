package com.refridge.core_server.product_recognition.domain.port;

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.product_recognition.infra.dto.REFRecognizedGroceryItemDto;

import java.util.Optional;

public interface REFGroceryItemQueryClient {
    Optional<REFRecognizedGroceryItemDto> getItemByName(String itemName);
}
