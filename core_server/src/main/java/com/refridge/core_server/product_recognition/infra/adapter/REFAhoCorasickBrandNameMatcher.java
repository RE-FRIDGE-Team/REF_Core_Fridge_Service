package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.product_recognition.domain.port.REFBrandMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType.BRAND;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFAhoCorasickBrandNameMatcher implements REFBrandMatcher {

    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY = BRAND.getRedisKey();

    /* 메모리에 캐싱된 Trie */
    private volatile Trie trie;

    @Override
    public Optional<String> findMatch(String input) {
        if (trie == null) {
            log.warn("BRAND Trie가 초기화되지 않았습니다. 필터링을 건너뜁니다.");
            return Optional.empty();
        }

        Collection<Emit> emits = trie.parseText(input);

        if (emits.isEmpty()) {
            return Optional.empty();
        }

        String matched = emits.iterator().next().getKeyword();
        log.debug("브랜드 매칭: input='{}', matched='{}'", input, matched);
        return Optional.of(matched);
    }

    @Override
    public void rebuild() {
        Set<String> entries = redisTemplate.opsForSet().members(REDIS_KEY);

        if (entries == null || entries.isEmpty()) {
            log.info("BRAND 사전이 비어있습니다. key={}", REDIS_KEY);
            this.trie = null;
            return;
        }

        this.trie = Trie.builder()
                .ignoreCase()
                .addKeywords(entries)
                .build();

        log.info("BRAND Trie 재빌드 완료. 항목 수: {}", entries.size());
    }

}
