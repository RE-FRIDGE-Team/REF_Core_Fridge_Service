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
import java.util.concurrent.TimeUnit;

/**
 * 전체 데이터셋(23,572개)을 한 번씩 실행하는 벤치마크.
 *
 * <p>목적:
 * <ul>
 *   <li>각 데이터가 어느 핸들러에서 인식 완료되는지 분포 확인</li>
 *   <li>AOP 카운터 (calls / completed / rejected) 를 통해
 *       Grafana에서 핸들러별 처리 비율을 정확하게 측정</li>
 *   <li>전체 데이터셋 기준 파이프라인 총 소요 시간 측정</li>
 * </ul>
 *
 * <p>JMH 모드:
 * <pre>
 *   Mode.SingleShotTime — warmup 없이 딱 1회 실행, 총 소요 시간 반환
 *   내부 루프로 fixtures 23,572개를 순서대로 한 번씩 처리
 *   → ops/s가 아닌 "23,572건 처리에 걸린 총 ms" 를 측정
 * </pre>
 *
 * <p>AOP 메트릭 수집:
 * <pre>
 *   벤치마크 종료 후 Prometheus 스냅샷을 찍으면
 *   각 핸들러별 calls / completed / rejected 절대값 확인 가능
 *   → 어떤 핸들러에서 몇 건이 처리됐는지 정확한 분포 파악
 * </pre>
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)         // 분포 측정 목적 — warmup 없이 cold-start 그대로 측정
@Measurement(iterations = 1)   // 딱 1회 실행 (23,572건 전체가 하나의 iteration)
@Fork(1)
@State(Scope.Benchmark)
public class REFFullDatasetRecognitionBenchmark {

    private static final long   FIXTURE_SEED = 42L;
    private static final String FIXTURE_PATH = "/benchmark_fixtures.json";

    private List<String>       fixtures;
    private REFBenchmarkConfig config;

    @Setup(Level.Trial)
    public void setUp(REFBenchmarkConfig config) throws Exception {
        this.config   = config;
        this.fixtures = loadFixtures();
    }

    // ─────────────────────────────────────────────────────────
    // 1. 전체 파이프라인 — 23,572건을 순서대로 한 번씩 실행
    //    Grafana에서 핸들러별 처리 분포 확인용 메인 벤치마크
    // ─────────────────────────────────────────────────────────
    @Benchmark
    public void fullDataset_pipeline(Blackhole bh) {
        for (String input : fixtures) {
            REFRecognitionRequestCommand cmd = new REFRecognitionRequestCommand(
                    input, UUID.randomUUID().toString()
            );
            bh.consume(config.appService.recognize(cmd));
        }
    }

    // ─────────────────────────────────────────────────────────
    // 2. ExclusionFilter 단독 — 비식재료 비율 측정
    //    전체 23,572건 중 몇 건이 비식재료인지 확인
    //    (AOP rejected.total 카운터가 찍힘)
    // ─────────────────────────────────────────────────────────
    @Benchmark
    public void fullDataset_exclusionFilter(Blackhole bh) {
        for (String input : fixtures) {
            REFRecognitionContext ctx = makeContextWithParsing(input);
            config.exclusionFilterHandler.handle(ctx);
            bh.consume(ctx);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 3. DictMatch 단독 — 사전 매칭 성공률 측정
    //    ExclusionFilter 없이 순수하게 사전 매칭 가능한 비율 확인
    // ─────────────────────────────────────────────────────────
    @Benchmark
    public void fullDataset_dictMatch(Blackhole bh) {
        for (String input : fixtures) {
            REFRecognitionContext ctx = makeContextWithParsing(input);
            config.groceryItemDictMatchHandler.handle(ctx);
            bh.consume(ctx);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 4. ProductIndexSearch 단독 — 색인 검색 성공률 측정
    // ─────────────────────────────────────────────────────────
    @Benchmark
    public void fullDataset_productIndexSearch(Blackhole bh) {
        for (String input : fixtures) {
            REFRecognitionContext ctx = makeContextWithParsing(input);
            config.productIndexSearchHandler.handle(ctx);
            bh.consume(ctx);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    /**
     * parser.parse()는 AOP 포인트컷 대상이 아니므로 카운터 오염 없이 파싱만 수행.
     * 핸들러 격리 벤치마크(2~4)에서 사전 파싱 준비용.
     */
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
     * 시드 고정 셔플로 로드. 동일 시드 → 매 실행마다 동일한 입력 순서.
     */
    private List<String> loadFixtures() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream(FIXTURE_PATH);
        if (is == null) {
            throw new IllegalStateException(
                    "benchmark_fixtures.json not found. " +
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
}