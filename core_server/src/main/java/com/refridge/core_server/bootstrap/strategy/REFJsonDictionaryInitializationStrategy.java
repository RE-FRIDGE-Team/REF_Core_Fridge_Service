package com.refridge.core_server.bootstrap.strategy;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import com.refridge.core_server.product_recognition.domain.vo.REFEntrySource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFJsonDictionaryInitializationStrategy implements REFDictionaryInitializationStrategy {

    private final ObjectMapper objectMapper;

    private static final String DICTIONARY_PATH = "init/";

    /**
     * JSON 파싱용 내부 record
     */
    private record DictionaryJson(
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("entries") List<String> entries
    ) {
        List<String> safeEntries() {
            return entries != null ? entries : List.of();
        }
    }

    @Override
    public boolean supports(REFRecognitionDictionaryType type) {
        return type == REFRecognitionDictionaryType.EXCLUSION
                || type == REFRecognitionDictionaryType.BRAND;
    }

    @Override
    public void initializeEntries(REFRecognitionDictionary dictionary) {
        List<String> entries = loadFromJson(dictionary.getDictType());
        if (entries.isEmpty()) return;

        dictionary.addEntries(Set.copyOf(entries), REFEntrySource.ADMIN);
        log.info("[JSON 초기화] {} 사전: {}개 항목 삽입", dictionary.getDictType(), entries.size());
    }

    @Override
    public void supplementMissingEntries(REFRecognitionDictionary dictionary) {
        List<String> jsonEntries = loadFromJson(dictionary.getDictType());
        if (jsonEntries.isEmpty()) return;

        Set<String> missing = jsonEntries.stream()
                .filter(entry -> !dictionary.hasEntry(entry))
                .collect(Collectors.toUnmodifiableSet());

        if (missing.isEmpty()) {
            log.info("[JSON 보완] {} 사전: 누락 항목 없음", dictionary.getDictType());
            return;
        }

        dictionary.addEntries(missing, REFEntrySource.ADMIN);
        log.info("[JSON 보완] {} 사전: {}개 항목 추가", dictionary.getDictType(), missing.size());
    }

    @Override
    public void removeDeletedEntries(REFRecognitionDictionary dictionary) {
        Set<String> jsonEntries = new HashSet<>(loadFromJson(dictionary.getDictType())); // exclusion_dict.json 로드

        // DB에는 있지만 JSON에는 없는 항목 → 삭제 대상
        Set<String> currentEntries = dictionary.getAllEntryTexts();
        Set<String> toRemove = currentEntries.stream()
                .filter(entry -> !jsonEntries.contains(entry))
                .collect(Collectors.toSet());

        if (!toRemove.isEmpty()) {
            toRemove.forEach(dictionary::removeEntry); // AR에 removeEntry 추가 필요
            log.info("[사전 정제] {} 항목 제거: {}개",
                    dictionary.getDictType().getKorDictName(), toRemove.size());
        }
    }

    private List<String> loadFromJson(REFRecognitionDictionaryType type) {
        String fileName = DICTIONARY_PATH + type.getInitFileName();
        ClassPathResource resource = new ClassPathResource(fileName);

        if (!resource.exists()) {
            log.warn("[JSON 로더] 파일 없음, 빈 사전으로 초기화: {}", fileName);
            return List.of();
        }

        try (InputStream is = resource.getInputStream()) {
            DictionaryJson data = objectMapper.readValue(is, DictionaryJson.class);
            validateType(data, type, fileName);
            return data.safeEntries();

        } catch (IOException e) {
            log.error("[JSON 로더] 파싱 실패: {}", fileName, e);
            return List.of();
        }
    }

    private void validateType(DictionaryJson data, REFRecognitionDictionaryType expected, String fileName) {
        if (data.type() == null || !expected.name().equalsIgnoreCase(data.type())) {
            log.warn("[JSON 로더] 타입 불일치 - 파일: {}, 기대: {}, 실제: {}",
                    fileName, expected.name(), data.type());
        }
    }
}
