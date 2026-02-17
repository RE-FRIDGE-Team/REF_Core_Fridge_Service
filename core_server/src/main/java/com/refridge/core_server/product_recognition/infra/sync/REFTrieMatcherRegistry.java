package com.refridge.core_server.product_recognition.infra.sync;

import com.refridge.core_server.product_recognition.domain.port.REFBrandMatcher;
import com.refridge.core_server.product_recognition.domain.port.REFExclusionWordMatcher;
import com.refridge.core_server.product_recognition.domain.port.REFGroceryItemDictionaryMatcher;
import com.refridge.core_server.product_recognition.domain.port.REFTrieBasedWordMatcher;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class REFTrieMatcherRegistry {

    private final Map<REFRecognitionDictionaryType, REFTrieBasedWordMatcher> matchers;

    public REFTrieMatcherRegistry(
            REFExclusionWordMatcher exclusionMatcher,
            REFGroceryItemDictionaryMatcher groceryItemMatcher,
            REFBrandMatcher brandMatcher
    ) {
        matchers = new EnumMap<>(REFRecognitionDictionaryType.class);
        matchers.put(REFRecognitionDictionaryType.EXCLUSION, exclusionMatcher);
        matchers.put(REFRecognitionDictionaryType.GROCERY_ITEM, groceryItemMatcher);
        matchers.put(REFRecognitionDictionaryType.BRAND, brandMatcher);
    }

    public REFTrieBasedWordMatcher getMatcher(REFRecognitionDictionaryType type) {
        return matchers.get(type);
    }
}