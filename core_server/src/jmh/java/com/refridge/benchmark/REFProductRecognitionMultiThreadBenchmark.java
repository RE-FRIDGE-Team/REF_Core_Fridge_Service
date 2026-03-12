package com.refridge.benchmark;

import com.refridge.core_server.product_recognition.application.dto.command.REFRecognitionRequestCommand;
import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-thread 파이프라인 벤치마크.
 *
 * <p>입력 전략:
 * <ul>
 *   <li>benchmark_fixtures.json (23,572개) 에서 시드 고정 셔플 후 순환 사용</li>
 *   <li>동일 시드 → 매 실행마다 동일한 입력 순서 보장</li>
 *   <li>{@code AtomicInteger} 인덱스로 스레드 간 입력 분산 (중복 없이 순환)</li>
 * </ul>
 *
 * <p>스레드 구성:
 * <pre>
 *   @Param threads: 1 / 4 / 8 / 16
 *   → Tomcat max-threads=200 환경에서 실제 동시 요청 수 시뮬레이션
 * </pre>
 *
 * <p>fixture 파일 위치: {@code src/jmh/resources/benchmark_fixtures.json}
 */
@BenchmarkMode(Mode.Throughput)          // ops/s — 동시성 측정엔 Throughput이 적합
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class REFProductRecognitionMultiThreadBenchmark {

    // ── 시드 고정 — 변경하면 입력 순서 달라짐 ──
    private static final long   FIXTURE_SEED   = 42L;
    private static final String FIXTURE_PATH   = "/benchmark_fixtures.json";

    // ── 스레드 수 파라미터 ──
    @Param({"1", "4", "8", "16"})
    private int threadCount;

    // ── 공유 픽스처 ──
    private List<String> fixtures;
    private AtomicInteger fixtureIndex;   // 스레드 간 순환 인덱스

    // ── Config에서 가져온 빈 ──
    private REFBenchmarkConfig config;

    // ────────────────────────────────────────
    // Setup
    // ────────────────────────────────────────

    @Setup(Level.Trial)
    public void setUp(REFBenchmarkConfig config) throws Exception {
        this.config = config;
        this.fixtures = loadAndShuffleFixtures();
        this.fixtureIndex = new AtomicInteger(0);
    }

    /**
     * 매 핸들러 호출 전 새 Context 생성.
     * threadCount만큼 스레드가 동시에 실행되므로 Invocation-level.
     */
    @Setup(Level.Invocation)
    public void prepareContext() {
        // 핸들러 단위 벤치마크에서 사용할 Context는 nextInput()으로 새로 만듦
        // (freshContext는 각 @Benchmark 메서드에서 로컬로 생성)
    }

    // ────────────────────────────────────────
    // 1. fullPipeline — 전체 파이프라인
    // ────────────────────────────────────────

    @Benchmark
    @Threads(1)   // @Param threadCount를 적용하려면 아래 concurrentFullPipeline 사용
    public void fullPipeline_single(Blackhole bh) {
        String input = nextInput();
        REFRecognitionRequestCommand cmd = new REFRecognitionRequestCommand(
                input, UUID.randomUUID().toString()
        );
        bh.consume(config.appService.recognize(cmd));
    }

    /**
     * threadCount @Param 적용 버전.
     * JMH @Threads는 어노테이션에 리터럴만 허용 → ExecutorService로 직접 제어.
     */
    @Benchmark
    public void fullPipeline_concurrent(Blackhole bh) throws Exception {
        runConcurrent(threadCount, bh, () -> {
            String input = nextInput();
            return config.appService.recognize(
                    new REFRecognitionRequestCommand(input, UUID.randomUUID().toString())
            );
        });
    }

    // ────────────────────────────────────────
    // 2. handler1 — 파싱 핸들러
    // ────────────────────────────────────────

    @Benchmark
    public void handler1_parsing_concurrent(Blackhole bh) throws Exception {
        runConcurrent(threadCount, bh, () -> {
            REFRecognitionContext ctx = makeContext(nextInput());
            config.productNameParsingHandler.handle(ctx);
            return ctx;
        });
    }

    // ────────────────────────────────────────
    // 3. handler2 — 비식재료 필터
    // ────────────────────────────────────────

    @Benchmark
    public void handler2_exclusionFilter_concurrent(Blackhole bh) throws Exception {
        runConcurrent(threadCount, bh, () -> {
            REFRecognitionContext ctx = makeContextWithParsing(nextInput());  // parser.parse() 직접 호출
            config.exclusionFilterHandler.handle(ctx);
            return ctx;
        });
    }

    // ────────────────────────────────────────
    // 4. handler3 — 식재료 사전 매칭
    // ────────────────────────────────────────

    @Benchmark
    public void handler3_dictMatch_concurrent(Blackhole bh) throws Exception {
        runConcurrent(threadCount, bh, () -> {
            REFRecognitionContext ctx = makeContextWithParsing(nextInput());
            config.groceryItemDictMatchHandler.handle(ctx);
            return ctx;
        });
    }

    // ────────────────────────────────────────
    // 5. handler4 — 제품 색인 검색 (현재 병목)
    // ────────────────────────────────────────

    @Benchmark
    public void handler4_productIndexSearch_concurrent(Blackhole bh) throws Exception {
        runConcurrent(threadCount, bh, () -> {
            REFRecognitionContext ctx = makeContextWithParsing(nextInput());
            config.productIndexSearchHandler.handle(ctx);
            return ctx;
        });
    }

    // ────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────

    /**
     * N개 스레드를 동시에 실행하고 모두 완료될 때까지 대기.
     * @Param threadCount를 동적으로 적용하기 위한 헬퍼.
     */
    private void runConcurrent(int n, Blackhole bh, ConcurrentTask task) throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(n);
        var futures  = new ArrayList<Future<?>>(n);

        for (int i = 0; i < n; i++) {
            futures.add(executor.submit(() -> task.execute()));
        }
        for (var f : futures) {
            bh.consume(f.get());
        }
        executor.shutdown();
    }

    /**
     * AtomicInteger 기반 순환 인덱스로 스레드 안전하게 다음 입력 반환.
     * fixtures 길이를 넘으면 0으로 돌아감 (무한 순환).
     */
    private String nextInput() {
        int idx = fixtureIndex.getAndUpdate(i -> (i + 1) % fixtures.size());
        return fixtures.get(idx);
    }

    /**
     * 핸들러 단위 테스트용 신선한 Context 생성.
     * DB 저장 없이 AR 인스턴스만 생성.
     */
    private REFRecognitionContext makeContext(String input) {
        REFProductRecognition recognition = REFProductRecognition.create(
                input, UUID.randomUUID().toString()
        );
        return new REFRecognitionContext(input, recognition);
    }

    private REFRecognitionContext makeContextWithParsing(String input) {
        REFProductRecognition recognition = REFProductRecognition.create(
                input, UUID.randomUUID().toString()
        );
        REFRecognitionContext ctx = new REFRecognitionContext(input, recognition);

        // parser.parse() 직접 호출 → AOP 미적용, calls.total 카운터 오염 없음
        REFParsedProductInformation parsed = config.parser.parse(input);
        ctx.setParsedProductName(parsed);
        return ctx;
    }

    /**
     * benchmark_fixtures.json 로드 후 시드 고정 셔플.
     * 시드가 동일하면 매 JMH 실행마다 동일한 순서 보장.
     */
    private List<String> loadAndShuffleFixtures() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream(FIXTURE_PATH);
        if (is == null) {
            throw new IllegalStateException(
                    "benchmark_fixtures.json not found in classpath. " +
                            "Place it at src/jmh/resources/benchmark_fixtures.json"
            );
        }
        JsonNode root  = mapper.readTree(is);
        JsonNode items = root.get("items");

        List<String> list = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            list.add(item.asString());
        }

        Collections.shuffle(list, new Random(FIXTURE_SEED));
        return Collections.unmodifiableList(list);
    }

    @FunctionalInterface
    private interface ConcurrentTask {
        Object execute() throws Exception;
    }
}