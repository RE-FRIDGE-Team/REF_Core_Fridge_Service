package com.refridge.benchmark;


import com.refridge.core_server.CoreServerApplication;
import com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService;
import com.refridge.core_server.product_recognition.domain.port.REFProductNameParser;
import com.refridge.core_server.product_recognition.infra.pipeline.*;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Scope.Benchmark: 전체 벤치마크 실행 중 단 하나의 인스턴스<p>
 * → Spring Context를 한 번만 띄우고 공유
 */
@State(Scope.Benchmark)
public class REFBenchmarkConfig {

    public ConfigurableApplicationContext context;

    // ── AppService ──
    public REFProductRecognitionAppService appService;

    // ── Parser ──
    public REFProductNameParser parser;

    // ── 개별 핸들러 (단계별 격리 측정용) ──
    public REFProductNameParsingHandler productNameParsingHandler;
    public REFExclusionFilterHandler exclusionFilterHandler;
    public REFGroceryItemDictMatchHandler groceryItemDictMatchHandler;
    public REFProductIndexSearchHandler productIndexSearchHandler;
    public REFMLPredictionHandler mlPredictionHandler;

    @Setup(Level.Trial)
    public void bootSpring() {
        System.setProperty("spring.profiles.active", "perf,benchmark");
        context = SpringApplication.run(CoreServerApplication.class);

        // Context 부트 후 빈 일괄 추출 (각 벤치마크 클래스에서 재추출 불필요)
        appService                  = context.getBean(REFProductRecognitionAppService.class);
        parser                      = context.getBean(REFProductNameParser.class);
        productNameParsingHandler   = context.getBean(REFProductNameParsingHandler.class);
        exclusionFilterHandler      = context.getBean(REFExclusionFilterHandler.class);
        groceryItemDictMatchHandler = context.getBean(REFGroceryItemDictMatchHandler.class);
        productIndexSearchHandler   = context.getBean(REFProductIndexSearchHandler.class);
        mlPredictionHandler         = context.getBean(REFMLPredictionHandler.class);
    }

    @TearDown(Level.Trial)
    public void shutdownSpring() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }
}