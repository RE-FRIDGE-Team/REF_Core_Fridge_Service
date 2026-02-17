package com.refridge.core_server.product_recognition.infra.sync;

import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFRecognitionDictionaryRedisSync {

    private final StringRedisTemplate redisTemplate;

    /**
     * Dictionary의 전체 entries를 Redis Set으로 동기화한다.
     * 기존 데이터를 삭제 후 새로 넣는 방식 (전체 교체)
     */
    public void sync(REFRecognitionDictionary dictionary) {
        String redisKey = resolveKey(dictionary.getDictType());
        Set<String> entries = dictionary.getAllEntryTexts();

        // 기존 삭제 후 재등록
        redisTemplate.delete(redisKey);

        if (!entries.isEmpty()) {
            redisTemplate.opsForSet().add(redisKey, entries.toArray(String[]::new));
        }

        log.info("Redis 사전 동기화 완료. key={}, 항목 수={}, version={}",
                redisKey, entries.size(), dictionary.getVersion());
    }

    private String resolveKey(REFRecognitionDictionaryType type) {
        return switch (type) {
            case EXCLUSION -> EXCLUSION.getRedisKey();
            case GROCERY_ITEM -> GROCERY_ITEM.getRedisKey();
            case BRAND -> BRAND.getRedisKey();
        };
    }
}
