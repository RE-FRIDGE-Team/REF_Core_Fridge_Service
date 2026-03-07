package com.refridge.core_server.bootstrap.strategy;

import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class REFGroceryItemDictionaryInitializationStrategy implements REFDictionaryInitializationStrategy{

    @Override
    public boolean supports(REFRecognitionDictionaryType type) {
        return type == REFRecognitionDictionaryType.GROCERY_ITEM;
    }

    @Override
    public void initializeEntries(REFRecognitionDictionary dictionary) {
        /*
         * TODO: GroceryItem DB 기반 초기화
         *
         * 선행 조건: @Order(1) REFGroceryItemCsvInitializer 가 CSV → ref_grocery_item 테이블 적재 완료
         *
         * 구현 방향:
         *   1. GroceryItemRepository.findAll() 로 전체 GroceryItem 조회
         *   2. GroceryItem의 대표 검색어 (name, aliases 등)를 추출
         *   3. dictionary.addEntries(extractedNames, REFEntrySource.DEFAULT)
         *
         * 고려사항:
         *   - GroceryItem이 수만 건일 경우 페이징 처리 필요 (Slice or Page)
         *   - 동의어/별칭 컬럼이 있다면 함께 등록하여 매칭률 향상
         */
        log.warn("[GroceryItem 초기화] 미구현 — GroceryItemRepository 주입 후 구현 예정");
    }

    @Override
    public void supplementMissingEntries(REFRecognitionDictionary dictionary) {
        /*
         * TODO: GroceryItem DB 기반 보완
         *
         * 구현 방향:
         *   1. GroceryItemRepository.findAll() 로 전체 GroceryItem 조회
         *   2. dictionary.hasEntry() 로 DB 사전에 없는 항목만 필터링
         *   3. dictionary.addEntries(missing, REFEntrySource.DEFAULT)
         *
         * 고려사항:
         *   - GroceryItem이 추가/삭제되는 경우를 고려해 정기 동기화 스케줄러와 연동 가능
         *   - 삭제된 GroceryItem의 사전 항목 제거 로직도 함께 고려 필요
         */
        log.warn("[GroceryItem 보완] 미구현 — GroceryItemRepository 주입 후 구현 예정");
    }
}
