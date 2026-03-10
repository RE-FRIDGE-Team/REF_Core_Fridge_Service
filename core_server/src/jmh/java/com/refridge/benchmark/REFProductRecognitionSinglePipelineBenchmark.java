package com.refridge.benchmark;


import com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService;
import com.refridge.core_server.product_recognition.application.dto.command.REFRecognitionRequestCommand;
import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.port.REFProductNameParser;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 단일 스레드(threads=1) 기준 파이프라인 벤치마크.<p>
 * "Single"의 의미 = 1개 스레드가 반복 호출 (병렬 측정 아님)<p>
 * <pre>
 * 측정 목적:
 * 1. fullPipeline  → API 전체 응답 시간 기준치 확보
 * 2. parserOnly    → 파서 단계 격리 (순수 CPU 비용)
 * 3. 각 핸들러     → 파이프라인 단계별 병목 위치 확인
 * </pre>
 * <pre>
 * 컨텍스트 격리:
 * 핸들러 호출 시 context.complete()/reject()로 상태가 변경되므로
 * {@code @Setup(Level.Invocation)}으로 매 호출마다 새 Context 생성.
 * (Invocation-level setup은 측정 오버헤드가 있으나 핸들러 단위 ms 수준에서 허용 가능)
 *  </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class REFProductRecognitionSinglePipelineBenchmark {

    // ── 입력 픽스처 ──
    private static final String INPUT_GENERAL  = "[무료보냉] 하겐다즈 1.89L 하프갤런 바닐라 / 대용량 [원산지:미국]";
    private static final String INPUT_SIMPLE   = "삼양 불닭볶음면 140g";

    // ── AppService 커맨드 (매 호출마다 new 생성 오버헤드 제거) ──
    private REFRecognitionRequestCommand command;

    // ── 핸들러 격리용: Invocation마다 새 Context ──
    private REFRecognitionContext freshContext;

    // ── Config에서 공유된 빈 참조 ──
    private REFBenchmarkConfig config;

    @Setup(Level.Trial)
    public void setUp(REFBenchmarkConfig config) {
        this.config = config;
        this.command = new REFRecognitionRequestCommand(
                INPUT_GENERAL,
                java.util.UUID.randomUUID().toString()
        );
    }

    /**
     * 매 핸들러 호출 전 새 Context 생성.
     * REFProductRecognition.create()는 저장 없이 AR 인스턴스만 생성.
     */
    @Setup(Level.Invocation)
    public void prepareContext() {
        REFProductRecognition recognition = REFProductRecognition.create(
                INPUT_GENERAL,
                java.util.UUID.randomUUID().toString()
        );
        freshContext = new REFRecognitionContext(INPUT_GENERAL, recognition);
    }

    // ────────────────────────────────────────────────
    // 1. 전체 파이프라인 (DB + Redis + 트랜잭션 포함)
    // ────────────────────────────────────────────────
    @Benchmark
    public void fullPipeline(Blackhole bh) {
        bh.consume(config.appService.recognize(command));
    }

    // ────────────────────────────────────────────────
    // 2. 파서 단계 격리 (순수 CPU — Aho-Corasick + 정규식)
    // ────────────────────────────────────────────────
    @Benchmark
    public void parserOnly(Blackhole bh) {
        bh.consume(config.parser.parse(INPUT_SIMPLE));
    }

    // ────────────────────────────────────────────────
    // 3. 단계별 핸들러 격리 측정
    //    → fullPipeline과 parserOnly의 차이 구간을 분해
    // ────────────────────────────────────────────────

    /** 1단계: 파싱 핸들러 (context에 파싱 결과 저장, complete 없음) */
    @Benchmark
    public void handler1_parsing(Blackhole bh) {
        config.productNameParsingHandler.handle(freshContext);
        bh.consume(freshContext);
    }

    /** 2단계: 비식재료 필터 (Aho-Corasick 매칭 → reject 가능) */
    @Benchmark
    public void handler2_exclusionFilter(Blackhole bh) {
        config.productNameParsingHandler.handle(freshContext); // 파싱 선행 필요
        config.exclusionFilterHandler.handle(freshContext);
        bh.consume(freshContext);
    }

    /** 3단계: 식재료 사전 매칭 (Redis 조회 포함) */
    @Benchmark
    public void handler3_dictMatch(Blackhole bh) {
        config.productNameParsingHandler.handle(freshContext);
        config.groceryItemDictMatchHandler.handle(freshContext);
        bh.consume(freshContext);
    }

    /** 4단계: 제품 색인 검색 (DB/Redis 조회) */
    @Benchmark
    public void handler4_productIndexSearch(Blackhole bh) {
        config.productNameParsingHandler.handle(freshContext);
        config.productIndexSearchHandler.handle(freshContext);
        bh.consume(freshContext);
    }
}