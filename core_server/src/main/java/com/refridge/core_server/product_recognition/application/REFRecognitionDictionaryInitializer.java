package com.refridge.core_server.product_recognition.application;


import com.refridge.core_server.product_recognition.domain.REFRecognitionDictionaryRepository;
import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryName;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import com.refridge.core_server.product_recognition.infra.sync.REFRecognitionDictionaryRedisSync;
import com.refridge.core_server.product_recognition.infra.sync.REFTrieMatcherRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFRecognitionDictionaryInitializer implements ApplicationRunner {

    private final REFRecognitionDictionaryRepository dictionaryRepository;
    private final REFRecognitionDictionaryRedisSync redisSync;
    private final REFTrieMatcherRegistry matcherRegistry;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (REFRecognitionDictionaryType type : REFRecognitionDictionaryType.values()) {
            // 1. DB에 사전이 없으면 생성
            initializeDictionaryIfAbsent(type);

            // 2. DB → Redis 동기화
            syncToRedis(type);

            // 3. Redis → Trie 빌드
            matcherRegistry.getMatcher(type).rebuild();
        }

        log.info("사전 초기화 완료: DB → Redis → Trie ({}개 사전)",
                REFRecognitionDictionaryType.values().length);
    }

    private void initializeDictionaryIfAbsent(REFRecognitionDictionaryType type) {
        boolean exists = dictionaryRepository.existsByDictTypeAndDictName(type, REFRecognitionDictionaryName.of(type.getKorDictName()));
        if (!exists) {
            REFRecognitionDictionary dictionary = switch (type) {
                case EXCLUSION -> REFRecognitionDictionary.createExclusionDictionary(type.getKorDictName());
                case GROCERY_ITEM -> REFRecognitionDictionary.createIngredientDictionary(type.getKorDictName());
            };
            dictionaryRepository.save(dictionary);
            log.info("사전 생성 완료: {}", type.getKorDictName());
        }
    }

    private void syncToRedis(REFRecognitionDictionaryType type) {
        dictionaryRepository.findByDictType(type)
                .ifPresent(redisSync::sync);
    }
}
