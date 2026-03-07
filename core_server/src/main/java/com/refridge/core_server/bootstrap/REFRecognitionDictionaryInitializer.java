package com.refridge.core_server.bootstrap;


import com.refridge.core_server.bootstrap.strategy.REFDictionaryInitializationStrategy;
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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class REFRecognitionDictionaryInitializer implements ApplicationRunner {

    private final REFRecognitionDictionaryRepository dictionaryRepository;
    private final REFRecognitionDictionaryRedisSync redisSync;
    private final REFTrieMatcherRegistry matcherRegistry;
    private final List<REFDictionaryInitializationStrategy> strategies; // Spring이 자동 주입

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (REFRecognitionDictionaryType type : REFRecognitionDictionaryType.values()) {
            REFDictionaryInitializationStrategy strategy = resolveStrategy(type);

            // 1. DB에 사전이 없으면 생성
            initializeDictionary(type, strategy);

            // 2. DB → Redis 동기화
            syncToRedis(type);

            // 3. Redis → Trie 빌드
            matcherRegistry.getMatcher(type).rebuild();
        }

        log.info("사전 초기화 완료: DB → Redis → Trie ({}개 사전)",
                REFRecognitionDictionaryType.values().length);
    }

    /**
     * 사전이 DB에 존재하지 않으면 초기 사전을 생성하는 메서드
     * @param type 사전 타입 (EXCLUSION, GROCERY_ITEM, BRAND)
     */
    @Transactional
    protected void initializeDictionary(REFRecognitionDictionaryType type,
                                        REFDictionaryInitializationStrategy strategy) {
        boolean exists = dictionaryRepository.existsByDictTypeAndDictName(
                type, REFRecognitionDictionaryName.of(type.getKorDictName()));

        if (!exists) {
            REFRecognitionDictionary dictionary = createEmptyDictionary(type);
            strategy.initializeEntries(dictionary);
            dictionaryRepository.save(dictionary);
            log.info("[사전 생성] {}", type.getKorDictName());
        } else {
            dictionaryRepository.findByDictType(type)
                    .ifPresent(strategy::supplementMissingEntries);
            log.info("[사전 보완] {}", type.getKorDictName());
        }
    }

    private REFRecognitionDictionary createEmptyDictionary(REFRecognitionDictionaryType type) {
        return switch (type) {
            case EXCLUSION    -> REFRecognitionDictionary.createExclusionDictionary(type.getKorDictName());
            case GROCERY_ITEM -> REFRecognitionDictionary.createIngredientDictionary(type.getKorDictName());
            case BRAND        -> REFRecognitionDictionary.createBrandDictionary(type.getKorDictName());
        };
    }

    private REFDictionaryInitializationStrategy resolveStrategy(REFRecognitionDictionaryType type) {
        return strategies.stream()
                .filter(s -> s.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "사전 초기화 전략 없음: " + type.name()));
    }

    private void syncToRedis(REFRecognitionDictionaryType type) {
        dictionaryRepository.findByDictType(type)
                .ifPresent(redisSync::sync);
    }
}
