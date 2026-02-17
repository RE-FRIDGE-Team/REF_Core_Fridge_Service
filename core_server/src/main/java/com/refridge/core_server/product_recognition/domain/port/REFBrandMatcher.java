package com.refridge.core_server.product_recognition.domain.port;

import java.util.Optional;

public interface REFBrandMatcher extends REFTrieBasedWordMatcher {
    Optional<String> findMatch(String input);
}
