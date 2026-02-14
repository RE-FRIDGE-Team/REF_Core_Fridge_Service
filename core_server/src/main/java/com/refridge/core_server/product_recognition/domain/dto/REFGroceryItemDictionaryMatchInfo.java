package com.refridge.core_server.product_recognition.domain.dto;

/**
 * 2번 분기: 식재료 사전 매칭 결과
 * 매칭된 식재료명만 들고 있고, 상세 정보는 GroceryItem Context에서 조회
 */
public record REFGroceryItemDictionaryMatchInfo(
        String matchedGroceryItemName
) {

    public static REFGroceryItemDictionaryMatchInfo of(String matchedGroceryItemName) {
        return new REFGroceryItemDictionaryMatchInfo(matchedGroceryItemName);
    }
}
