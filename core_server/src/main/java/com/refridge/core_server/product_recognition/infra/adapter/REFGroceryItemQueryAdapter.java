package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemWithCategoryPathDto;
import com.refridge.core_server.product_recognition.domain.port.REFGroceryItemQueryClient;
import com.refridge.core_server.product_recognition.infra.dto.REFRecognizedGroceryItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemQueryAdapter implements REFGroceryItemQueryClient {

    private final REFGroceryItemRepository repository;

    @Override
    public Optional<REFRecognizedGroceryItemDto> getItemByName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            log.warn("getItemByName 호출 시 itemName이 null 또는 빈 문자열입니다.");
            return Optional.empty();
        }

        return repository.findByGroceryItemName(itemName)
                .map(this::toRecognizedDto);
    }

    /**
     * GroceryItem Context의 DTO를 ProductRecognition Context의 DTO로 변환.
     * Anti-Corruption Layer 역할 수행.
     */
    private REFRecognizedGroceryItemDto toRecognizedDto(REFGroceryItemWithCategoryPathDto dto) {
        return REFRecognizedGroceryItemDto.of(
                dto.groceryItemId(),
                dto.groceryItemName(),
                dto.categoryPath(),
                dto.representativeImageUrl()
        );
    }
}
