package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.product_recognition.domain.port.REFGroceryItemQueryClient;
import com.refridge.core_server.product_recognition.infra.dto.REFRecognizedGroceryItemDto;
import com.refridge.core_server.product_recognition.infra.mapper.REFRecognizedGroceryItemDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemQueryAdapter implements REFGroceryItemQueryClient {

    private final REFGroceryItemRepository repository;
    private final REFRecognizedGroceryItemDtoMapper mapper;

    @Override
    public Optional<REFRecognizedGroceryItemDto> getItemByName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            log.warn("getItemByName 호출 시 itemName이 null 또는 빈 문자열입니다.");
            return Optional.empty();
        }

        return repository.findByGroceryItemName(itemName)
                .map(mapper::toRecognizedDto);
    }
}
