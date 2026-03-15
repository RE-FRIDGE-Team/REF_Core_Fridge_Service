package com.refridge.benchmark;

import com.refridge.core_server.CoreServerApplication;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService;
import com.refridge.core_server.product_recognition.domain.port.REFExclusionWordMatcher;
import com.refridge.core_server.product_recognition.domain.port.REFProductNameParser;
import com.refridge.core_server.product_recognition.infra.pipeline.*;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

@State(Scope.Benchmark)
public class REFBenchmarkConfig {

    public ConfigurableApplicationContext context;

    // ── AppService ──
    public REFProductRecognitionAppService appService;

    // ── Parser ──
    public REFProductNameParser parser;

    // ── 개별 핸들러 ──
    public REFProductNameParsingHandler productNameParsingHandler;
    public REFExclusionFilterHandler exclusionFilterHandler;
    public REFGroceryItemDictMatchHandler groceryItemDictMatchHandler;
    public REFProductIndexSearchHandler productIndexSearchHandler;
    public REFMLPredictionHandler mlPredictionHandler;

    // ── ExclusionWordMatcher (오탐 리포트용) ──
    public REFExclusionWordMatcher exclusionWordMatcher;

    // ── GroceryItemRepository (CSV 내보내기 시 itemType 배치 조회용) ──
    public REFGroceryItemRepository groceryItemRepository;

    @Setup(Level.Trial)
    public void bootSpring() {
        System.setProperty("spring.profiles.active", "perf,benchmark");
        context = SpringApplication.run(CoreServerApplication.class);

        appService                  = context.getBean(REFProductRecognitionAppService.class);
        parser                      = context.getBean(REFProductNameParser.class);
        productNameParsingHandler   = context.getBean(REFProductNameParsingHandler.class);
        exclusionFilterHandler      = context.getBean(REFExclusionFilterHandler.class);
        groceryItemDictMatchHandler = context.getBean(REFGroceryItemDictMatchHandler.class);
        productIndexSearchHandler   = context.getBean(REFProductIndexSearchHandler.class);
        mlPredictionHandler         = context.getBean(REFMLPredictionHandler.class);
        exclusionWordMatcher        = context.getBean(REFExclusionWordMatcher.class);
        groceryItemRepository       = context.getBean(REFGroceryItemRepository.class);
    }

    @TearDown(Level.Trial)
    public void shutdownSpring() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }
}