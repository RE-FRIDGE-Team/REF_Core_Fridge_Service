package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.product_recognition.domain.port.REFExclusionWordMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType.EXCLUSION;


@Slf4j
@Component
@RequiredArgsConstructor
public class REFAhoCorasickExclusionWordMatcher implements REFExclusionWordMatcher {

    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY = EXCLUSION.getRedisKey();

    /* 메모리에 캐싱된 Trie */
    private volatile Trie trie;

    @Override
    public Optional<String> findMatch(String input) {
        if (trie == null) {
            log.warn("Exclusion Trie가 초기화되지 않았습니다. 필터링을 건너뜁니다.");
            return Optional.empty();
        }

        Collection<Emit> emits = trie.parseText(input);

        if (emits.isEmpty()) {
            return Optional.empty();
        }

        String matched = emits.iterator().next().getKeyword();
        log.debug("비식재료 키워드 매칭: input='{}', matched='{}'", input, matched);
        return Optional.of(matched);
    }

    @Override
    public List<String> findAllMatches(String input) {
        Trie currentTrie = this.trie;

        if (currentTrie == null) {
            log.warn("Exclusion Trie가 초기화되지 않았습니다.");
            return Collections.emptyList();
        }

        Collection<Emit> emits = currentTrie.parseText(input);

        List<String> keywords = emits.stream()
                .map(Emit::getKeyword)
                .toList();

        if (!keywords.isEmpty()) {
            log.debug("비식재료 키워드 전체 매칭: input='{}', matched={}", input, keywords);
        }

        return keywords;
    }

    /**
     * 서버 기동 시 또는 배치 이벤트 수신 시 호출.
     * 새 Trie를 완성한 뒤 참조를 교체하므로 읽기 중인 스레드에 영향 없음.
     */
    @Override
    public void rebuild() {
        Set<String> entries = redisTemplate.opsForSet().members(REDIS_KEY);

        if (entries == null || entries.isEmpty()) {
            log.info("Exclusion 사전이 비어있습니다. key={}", REDIS_KEY);
            this.trie = null;
            return;
        }

        this.trie = Trie.builder()
                .ignoreCase()
                .ignoreOverlaps()
                .addKeywords(entries)
                .build();
        log.info("Exclusion Trie 재빌드 완료. 항목 수: {}", entries.size());
    }
}
