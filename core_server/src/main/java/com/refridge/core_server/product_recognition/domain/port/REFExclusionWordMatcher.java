package com.refridge.core_server.product_recognition.domain.port;

import java.util.List;
import java.util.Optional;


public interface REFExclusionWordMatcher {

    /**
     * @param input 제품명 텍스트
     * @return 매칭된 비식재료 키워드 (없으면 empty)
     */
    Optional<String> findMatch(String input);

    List<String> findAllMatches(String input);

    void rebuild();
}
