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

    @Override
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

        // 1. Word Boundary 필터링
        List<Emit> boundaryEmits = emits.stream()
                .filter(emit -> isWordBoundary(input, emit))
                .toList();

        if (boundaryEmits.isEmpty()) {
            log.debug("Boundary 조건 미충족으로 매칭 없음: input='{}'", input);
            return Optional.empty();
        }

        // 2. 중복 키워드 제거 후 Longest Match 시도
        List<String> matchedKeywords = boundaryEmits.stream()
                .map(Emit::getKeyword)
                .distinct()
                .toList();

        if (matchedKeywords.size() == 1) {
            String matched = matchedKeywords.getFirst();
            log.debug("식재료 사전 매칭 성공: input='{}', matched='{}'", input, matched);
            return Optional.of(REFGroceryItemDictionaryMatchInfo.of(matched));
        }

        // 3. 2개 이상 매칭 → Longest Match로 포함 관계 해소 시도
        return tryResolveLongestMatch(input, matchedKeywords);
    }

    @Override
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

    /**
     * 2개 이상의 키워드가 매칭됐을 때, 모든 다른 키워드를 포함하는 Longest Match가
     * 존재하는지 확인하여 단일 식재료로 특정 가능한 경우 반환합니다.
     *
     * <pre>
     * 케이스 A: "치즈 돈까스" 입력 → "돈까스", "치즈 돈까스" 매칭
     *   → "돈까스"가 "치즈 돈까스"에 포함됨 → "치즈 돈까스" 반환
     *
     * 케이스 B: "돈까스 파스타" 입력 → "돈까스", "파스타" 매칭
     *   → 서로 포함 관계 없음 → Optional.empty() (다음 파이프라인으로)
     * </pre>
     */
    private Optional<REFGroceryItemDictionaryMatchInfo> tryResolveLongestMatch(
            String input, List<String> matchedKeywords) {

        // 가장 긴 키워드가 나머지 모든 키워드를 포함하는지 검사
        String longest = matchedKeywords.stream()
                .max(java.util.Comparator.comparingInt(String::length))
                .orElse(null);

        if (longest == null) return Optional.empty();

        boolean allContained = matchedKeywords.stream()
                .filter(kw -> !kw.equals(longest))
                .allMatch(longest::contains);

        if (allContained) {
            log.debug("Longest Match 해소 성공: input='{}', longest='{}', 포함된 키워드={}",
                    input, longest, matchedKeywords);
            return Optional.of(REFGroceryItemDictionaryMatchInfo.of(longest));
        }

        // 진짜 다른 두 식재료가 동시에 매칭됨 → 다음 파이프라인으로
        log.debug("2개 이상 독립 키워드 매칭, 다음 파이프라인으로: input='{}', matched={}",
                input, matchedKeywords);
        return Optional.empty();
    }

    /**
     * 매칭된 키워드가 입력 문자열에서 단어 경계를 만족하는지 확인합니다.
     *
     * <h3>GroceryItem 특화 경계 정의</h3>
     * <p>
     * ExclusionFilter와 달리, 우측 경계에 숫자를 포함합니다.
     * "무100g", "닭가슴살500g"처럼 수량/용량이 바로 붙어오는 케이스에서
     * 식재료명을 올바르게 잡기 위함입니다.
     * </p>
     *
     * <pre>
     * "안무용 토슈즈"에서 "무" 매칭:
     *   좌측: '안' → 한글, boundary 아님 → false → 올바르게 필터링
     *
     * "무 100g"에서 "무" 매칭:
     *   좌측: 문자열 시작 → boundary
     *   우측: ' ' → boundary → true → 올바르게 통과
     *
     * "무100g"에서 "무" 매칭:
     *   좌측: 문자열 시작 → boundary
     *   우측: '1' → 숫자 → boundary → true → 올바르게 통과
     * </pre>
     */
    private boolean isWordBoundary(String input, Emit emit) {
        int start = emit.getStart();
        int end = emit.getEnd(); // Aho-Corasick end는 inclusive

        boolean leftBoundary = (start == 0) || isLeftBoundaryChar(input.charAt(start - 1));
        boolean rightBoundary = (end == input.length() - 1) || isRightBoundaryChar(input.charAt(end + 1));

        return leftBoundary && rightBoundary;
    }

    /**
     * 좌측 경계 문자 판단.
     * 공백, 괄호류, 구분자 → 경계로 인정
     */
    private boolean isLeftBoundaryChar(char c) {
        return Character.isWhitespace(c)
                || c == '[' || c == ']'
                || c == '(' || c == ')'
                || c == '/' || c == ','
                || c == '-' || c == '_';
    }

    /**
     * 우측 경계 문자 판단.
     * 공백, 괄호류, 구분자 외에 숫자도 경계로 인정 (수량/용량 붙여쓰기 허용).
     */
    private boolean isRightBoundaryChar(char c) {
        return Character.isWhitespace(c)
                || c == '[' || c == ']'
                || c == '(' || c == ')'
                || c == '/' || c == ','
                || c == '-' || c == '_'
                || Character.isDigit(c);
    }
}