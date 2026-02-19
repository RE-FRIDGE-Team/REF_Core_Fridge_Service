package com.refridge.core_server.product_recognition.domain.port;

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.product_recognition.infra.dto.REFRecognizedGroceryItemDto;

import java.util.Optional;

public interface REFGroceryItemQueryClient {

    // TODO : Entity를 받지 말고 Dto로 받아서 Id, Name, CategoryPath, ImageUrl 까지 포함해서 얻어와야 함.
    Optional<REFRecognizedGroceryItemDto> getItemByName(String itemName);
}
