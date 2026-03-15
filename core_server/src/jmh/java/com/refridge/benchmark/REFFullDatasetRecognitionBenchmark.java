package com.refridge.benchmark;

import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemItemTypeDto;
import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

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
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(1)
@State(Scope.Benchmark)
public class REFFullDatasetRecognitionBenchmark {

    private static final long   FIXTURE_SEED   = 42L;
    private static final String FIXTURE_PATH   = "/benchmark_fixtures.json";
    private static final String CSV_SEPARATOR  = " > ";

    // CSV 대상: DictMatch / ProductIndexSearch 에서 완료된 경우만
    private static final Set<String> CSV_TARGET_HANDLERS = Set.of(
            "GroceryItemDictMatch",
            "ProductIndexSearch"
    );

    private List<String>       fixtures;
    private REFBenchmarkConfig config;

    @Setup(Level.Trial)
    public void setUp(REFBenchmarkConfig config) throws Exception {
        this.config   = config;
        this.fixtures = loadFixtures();
    }

    @Benchmark
    public void fullDataset_pipeline(Blackhole bh) throws Exception {
        List<PipelineMatchResult> matchResults = new ArrayList<>();
        List<String> noMatchResults = new ArrayList<>();  // ← 추가

        for (String input : fixtures) {
            REFParsedProductInformation parsed = config.parser.parse(input);

            REFProductRecognition recognition = REFProductRecognition.create(
                    input, UUID.randomUUID().toString()
            );
            REFRecognitionContext ctx = new REFRecognitionContext(input, recognition);
            ctx.setParsedProductName(parsed);

            config.exclusionFilterHandler.handle(ctx);
            if (!ctx.isCompleted()) config.groceryItemDictMatchHandler.handle(ctx);
            if (!ctx.isCompleted()) config.productIndexSearchHandler.handle(ctx);
            if (!ctx.isCompleted()) config.mlPredictionHandler.handle(ctx);

            bh.consume(ctx);

            if (ctx.isCompleted() && ctx.getOutput() != null
                    && CSV_TARGET_HANDLERS.contains(ctx.getCompletedBy())) {
                String brand = parsed.getBrandName().orElse(null);
                matchResults.add(new PipelineMatchResult(input, ctx.getOutput(), brand, ctx.getCompletedBy()));

            } else if (!ctx.isCompleted() || ctx.getOutput() == null) {  // ← 추가
                // ExclusionFilter 반려 제외, 순수 노매치만 수집
                if (ctx.getOutput() == null && !isRejected(ctx)) {
                    noMatchResults.add(input);
                }
            }
        }

        writeCsv(matchResults);
        writeNoMatchCsv(noMatchResults);  // ← 추가
        System.out.printf("[CSV] 전체: %d건, 매칭 성공: %d건, 노매치: %d건%n",
                fixtures.size(), matchResults.size(), noMatchResults.size());
    }


    // ─────────────────────────────────────────────────────────
    // 2. ExclusionFilter 오탐 리포트
    // ─────────────────────────────────────────────────────────
    @Benchmark
    public void fullDataset_exclusionFilter(Blackhole bh) throws Exception {
        Map<String, List<String>> rejectedByKeyword = new TreeMap<>();

        for (String input : fixtures) {
            REFRecognitionContext ctx = makeContextWithParsing(input);
            List<String> matched = config.exclusionWordMatcher.findAllMatches(input);

            if (!matched.isEmpty()) {
                String refinedInput = ctx.getEffectiveInput();
                for (String keyword : matched) {
                    rejectedByKeyword
                            .computeIfAbsent(keyword, k -> new ArrayList<>())
                            .add(String.format("[원본] %s | [정제] %s", input, refinedInput));
                }
            }
            bh.consume(ctx);
        }

        StringBuilder report = new StringBuilder("=== ExclusionFilter 키워드별 반려 리포트 ===\n\n");
        rejectedByKeyword.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .forEach(entry -> {
                    report.append(String.format("[키워드: \"%s\"] %d건\n",
                            entry.getKey(), entry.getValue().size()));
                    entry.getValue().stream().limit(5)
                            .forEach(item -> report.append("  → ").append(item).append("\n"));
                    report.append("\n");
                });

        Path outputPath = Path.of("build/reports/jmh/rejection_by_keyword.txt");
        Files.write(outputPath, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(report);
    }

    // ─────────────────────────────────────────────────────────
    // 3. DictMatch 단독 성공률 측정
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
    // 4. ProductIndexSearch 단독 성공률 측정
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
    // CSV 출력
    // ─────────────────────────────────────────────────────────

    /**
     * 수집된 결과로 CSV 파일 생성.
     *
     * <p>itemType 은 groceryItemId 기준 배치 조회 (N+1 방지).
     * 쿼리 1회로 전체 itemType 맵 구성 후 루프에서 조회.
     *
     * <p>출력 경로: build/reports/jmh/recognition_results.csv
     * <p>CSV 컬럼: 실제품명, 대분류, 중분류, 카테고리태그, 식재료명, 브랜드
     */
    private void writeCsv(List<PipelineMatchResult> results) throws Exception {
        if (results.isEmpty()) {
            System.out.println("[CSV] 매칭된 항목 없음");
            return;
        }

        // ── groceryItemId 목록 수집 → 배치 조회로 itemType 획득 ──
        List<Long> groceryItemIds = results.stream()
                .map(r -> r.output().getGroceryItemId())
                .distinct()
                .collect(Collectors.toList());

        // Map<groceryItemId, REFInventoryItemType> — 배치 쿼리 1회
        Map<Long, REFInventoryItemType> itemTypeMap =
                config.groceryItemRepository.findItemTypesByIds(groceryItemIds)
                        .stream()
                        .collect(Collectors.toMap(
                                REFGroceryItemItemTypeDto::id,
                                REFGroceryItemItemTypeDto::itemType
                        ));

        // ── CSV 작성 ──
        StringBuilder csv = new StringBuilder();
        csv.append("실제품명,대분류,중분류,카테고리태그,식재료명,브랜드\n");

        for (PipelineMatchResult result : results) {
            REFProductRecognitionOutput output = result.output();

            // "대분류 > 중분류" 분리
            String[] parts = output.getCategoryPath().split(CSV_SEPARATOR, 2);
            String majorCategory = parts.length > 0 ? parts[0].trim() : "";
            String minorCategory = parts.length > 1 ? parts[1].trim() : "";

            // itemType (배치 조회 결과)
            REFInventoryItemType itemType = itemTypeMap.get(output.getGroceryItemId());
            String categoryTag = itemType != null ? itemType.name() : "";

            csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                    escapeCsv(result.rawInput()),
                    escapeCsv(majorCategory),
                    escapeCsv(minorCategory),
                    escapeCsv(categoryTag),
                    escapeCsv(output.getGroceryItemName()),
                    escapeCsv(result.brand() != null ? result.brand() : "")
            ));
        }

        Path outputPath = Path.of("build/reports/jmh/recognition_results.csv");
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, csv.toString().getBytes(StandardCharsets.UTF_8));
        System.out.printf("[CSV] 저장 완료: %s (%d건)%n", outputPath, results.size());
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private REFRecognitionContext makeContextWithParsing(String input) {
        REFProductRecognition recognition = REFProductRecognition.create(
                input, UUID.randomUUID().toString()
        );
        REFRecognitionContext ctx = new REFRecognitionContext(input, recognition);
        REFParsedProductInformation parsed = config.parser.parse(input);
        ctx.setParsedProductName(parsed);
        return ctx;
    }

    /** CSV 필드 이스케이프: 쉼표/개행/쌍따옴표 포함 시 쌍따옴표로 감쌈 */
    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

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

    /** ExclusionFilter 반려 여부 확인 */
    private boolean isRejected(REFRecognitionContext ctx) {
        return ctx.isCompleted()
                && ctx.getOutput() == null
                && "ExclusionFilter".equals(ctx.getCompletedBy());
    }

    /**
     * 노매치 항목 CSV 출력.
     * 제품명만 채우고 나머지 5개 컬럼은 전부 빈 값.
     * 출력 경로: build/reports/jmh/no_matched_recognition_result.csv
     */
    private void writeNoMatchCsv(List<String> noMatchInputs) throws Exception {
        if (noMatchInputs.isEmpty()) {
            System.out.println("[CSV] 노매치 항목 없음");
            return;
        }

        StringBuilder csv = new StringBuilder();
        csv.append("실제품명,대분류,중분류,카테고리태그,식재료명,브랜드\n");

        for (String input : noMatchInputs) {
            csv.append(String.format("%s,,,,,\n", escapeCsv(input)));
        }

        Path outputPath = Path.of("build/reports/jmh/no_matched_recognition_result.csv");
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, csv.toString().getBytes(StandardCharsets.UTF_8));
        System.out.printf("[CSV] 노매치 저장 완료: %s (%d건)%n", outputPath, noMatchInputs.size());
    }

    /** 파이프라인 매칭 결과 중간 수집용 레코드 */
    private record PipelineMatchResult(
            String rawInput,
            REFProductRecognitionOutput output,
            String brand,        // null 가능 — 파서가 브랜드 못 찾은 경우
            String completedBy   // "GroceryItemDictMatch" or "ProductIndexSearch"
    ) {}
}