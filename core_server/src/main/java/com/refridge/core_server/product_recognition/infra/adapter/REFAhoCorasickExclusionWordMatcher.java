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
        Trie currentTrie = this.trie;

        if (currentTrie == null) {
            log.warn("Exclusion Trie가 초기화되지 않았습니다. 필터링을 건너뜁니다.");
            return Optional.empty();
        }

        Collection<Emit> emits = currentTrie.parseText(input);

        if (emits.isEmpty()) {
            return Optional.empty();
        }

        return emits.stream()
                .filter(emit -> isWordBoundary(input, emit))
                .map(Emit::getKeyword)
                .peek(matched ->
                        log.debug("비식재료 키워드 매칭: input='{}', matched='{}'", input, matched))
                .findFirst();
    }

    @Override
    public List<String> findAllMatches(String input) {
        Trie currentTrie = this.trie;

        if (currentTrie == null) {
            log.warn("Exclusion Trie가 초기화되지 않았습니다.");
            return Collections.emptyList();
        }

        Collection<Emit> emits = currentTrie.parseText(input);

        if (emits.isEmpty()) {
            return List.of();
        }

        return emits.stream()
                .filter(emit -> isWordBoundary(input, emit))
                .map(Emit::getKeyword)
                .peek(matched ->
                        log.debug("비식재료 키워드 매칭: input='{}', matched='{}'", input, matched))
                .toList();
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

    /**
     * 매칭된 키워드가 독립된 단어인지 확인합니다.<p>
     * <pre>
     * "우드앤브릭"에서 "우드"가 매칭된 경우 → false (경계 아님)
     * "[우드] 헤이즐넛"에서 "우드"가 매칭된 경우 → true (경계 맞음)
     * </pre>
     */
    private boolean isWordBoundary(String input, Emit emit) {
        int start = emit.getStart();
        int end = emit.getEnd(); // Aho-Corasick의 end는 inclusive

        boolean leftBoundary = (start == 0) || isBoundaryChar(input.charAt(start - 1));
        boolean rightBoundary = (end == input.length() - 1) || isBoundaryChar(input.charAt(end + 1));

        return leftBoundary && rightBoundary;
    }

    private boolean isBoundaryChar(char c) {
        // 공백, 대괄호, 소괄호, 특수문자 등을 경계로 인정
        return Character.isWhitespace(c)
                || c == '['  || c == ']'
                || c == '('  || c == ')'
                || c == '/'  || c == ','
                || c == '-'  || c == '_';
    }
}
