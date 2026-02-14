package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.product_recognition.domain.dto.REFGroceryItemDictionaryMatchInfo;
import com.refridge.core_server.product_recognition.domain.port.REFGroceryItemDictionaryMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType.GROCERY_ITEM;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFAhoCorasickGroceryItemDictionaryMatcher implements REFGroceryItemDictionaryMatcher {

    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY = GROCERY_ITEM.getRedisKey();

    private volatile Trie trie;

    public Optional<REFGroceryItemDictionaryMatchInfo> findMatch(String input) {
        if (this.trie == null) {
            log.warn("GroceryItem Trie가 초기화되지 않았습니다. 사전 매칭을 건너뜁니다.");
            return Optional.empty();
        }

        Collection<Emit> emits = trie.parseText(input);

        if (emits.isEmpty()) {
            log.debug("사전 매칭 결과 없음: input='{}'", input);
            return Optional.empty();
        }

        // 중복 제거된 매칭 키워드 목록
        List<String> matchedKeywords = emits.stream()
                .map(Emit::getKeyword)
                .distinct()
                .toList();

        // 2개 이상 매칭 → 정확한 원재료 특정 불가
        if (matchedKeywords.size() >= 2) {
            log.debug("사전 매칭 2개 이상, 다음 분기로 넘김: input='{}', matched={}", input, matchedKeywords);
            return Optional.empty();
        }

        // 정확히 1개 매칭
        String matched = matchedKeywords.getFirst();
        log.debug("식재료 사전 매칭 성공: input='{}', matched='{}'", input, matched);
        return Optional.of(REFGroceryItemDictionaryMatchInfo.of(matched));
    }

    /**
     * 서버 기동 시 또는 배치 이벤트 수신 시 호출.
     */
    public void rebuild() {
        Set<String> entries = redisTemplate.opsForSet().members(REDIS_KEY);

        if (entries == null || entries.isEmpty()) {
            log.info("GroceryItem 사전이 비어있습니다. key={}", REDIS_KEY);
            this.trie = null;
            return;
        }

        this.trie = Trie.builder()
                .ignoreCase()
                .addKeywords(entries)
                .build();
        log.info("GroceryItem Trie 재빌드 완료. 항목 수: {}", entries.size());
    }

}
