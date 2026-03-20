package com.refridge.core_server.product_recognition.application;

import com.refridge.core_server.product_recognition.application.dto.result.REFCachedPipelineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 파이프라인 실행 결과를 캐싱하는 서비스.
 * 동일한 inputText에 대해 파이프라인을 반복 실행하지 않도록 한다.
 * AR(이력) 저장과는 분리되어, 이력은 항상 기록되고 파이프라인 연산만 캐싱된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFRecognitionPipelineCacheService {

    private final CacheManager cacheManager;

    private static final String CACHE_NAME = "recognition:pipeline-result";

    /**
     * 캐시에서 파이프라인 결과를 조회한다.
     * @param inputText 원본 제품명
     * @return 캐시된 결과 (없으면 empty)
     */
    public Optional<REFCachedPipelineResult> getCachedResult(String inputText) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return Optional.empty();
        }

        Cache.ValueWrapper wrapper = cache.get(inputText);
        if (wrapper == null) {
            return Optional.empty();
        }

        REFCachedPipelineResult result = (REFCachedPipelineResult) wrapper.get();
        log.debug("[PipelineCache] 캐시 히트: input='{}'", inputText);
        return Optional.ofNullable(result);
    }

    /**
     * 파이프라인 결과를 캐시에 저장한다.
     * @param inputText 원본 제품명
     * @param result 파이프라인 실행 결과
     */
    public void cacheResult(String inputText, REFCachedPipelineResult result) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(inputText, result);
            log.debug("[PipelineCache] 캐시 저장: input='{}', completedBy='{}'",
                    inputText, result.completedBy());
        }
    }
}