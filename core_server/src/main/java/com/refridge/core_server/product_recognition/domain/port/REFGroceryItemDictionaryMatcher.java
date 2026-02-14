package com.refridge.core_server.product_recognition.domain.port;

import com.refridge.core_server.product_recognition.domain.dto.REFGroceryItemDictionaryMatchInfo;

import java.util.Optional;

public interface REFGroceryItemDictionaryMatcher {

    Optional<REFGroceryItemDictionaryMatchInfo> findMatch(String input);

     void rebuild();
}
