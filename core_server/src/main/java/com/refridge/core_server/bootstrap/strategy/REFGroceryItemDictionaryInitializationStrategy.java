package com.refridge.core_server.bootstrap.strategy;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.vo.REFEntrySource;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemDictionaryInitializationStrategy implements REFDictionaryInitializationStrategy{

    private final REFGroceryItemRepository groceryItemRepository;

    @Override
    public boolean supports(REFRecognitionDictionaryType type) {
        return type == REFRecognitionDictionaryType.GROCERY_ITEM;
    }

    @Override
    public void initializeEntries(REFRecognitionDictionary dictionary) {
        dictionary.addEntries(groceryItemRepository.findAllGroceryItemNames(), REFEntrySource.ADMIN);
        log.info("[GroceryItem 초기화] {} 사전: {}개 항목 삽입", dictionary.getDictType(), groceryItemRepository.count());
    }

    @Override
    public void supplementMissingEntries(REFRecognitionDictionary dictionary) {
        /*
         * 고려사항:
         *   - GroceryItem이 추가/삭제되는 경우를 고려해 정기 동기화 스케줄러와 연동 가능
         *   - 삭제된 GroceryItem의 사전 항목 제거 로직도 함께 고려 필요
         */

       dictionary.addEntries(groceryItemRepository.findAllGroceryItemNames().stream()
               .filter(name -> !dictionary.hasEntry(name))
               .collect(Collectors.toSet()), REFEntrySource.ADMIN);

        log.warn("[GroceryItem 보완] {} 사전에 누락된 항목 보완 완료", dictionary.getDictType());
    }
}
