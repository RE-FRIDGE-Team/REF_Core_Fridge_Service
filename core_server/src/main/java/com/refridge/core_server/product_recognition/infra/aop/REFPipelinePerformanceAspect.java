package com.refridge.core_server.product_recognition.infra.aop;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@Profile("perf")   // ← 원래대로 유지. perf 포함 시에만 활성화
public class REFPipelinePerformanceAspect {

    private final MeterRegistry meterRegistry;

    // 매 호출마다 Timer/Counter를 새로 만들지 않기 위한 캐시
    // computeIfAbsent → 최초 1회만 생성, 이후 읽기만 발생 (경합 없음)
    private final ConcurrentHashMap<String, Timer>   timerCache     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> callsCache     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> completedCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> rejectedCache  = new ConcurrentHashMap<>();

    public REFPipelinePerformanceAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // ── 핸들러 단계별 측정 ──────────────────────────────
    @Around("execution(* com.refridge.core_server.product_recognition.infra.pipeline.*.handle(..))")
    public Object measureHandler(ProceedingJoinPoint pjp) throws Throwable {
        String handlerName = pjp.getTarget().getClass().getSimpleName()
                .replace("REF", "").replace("Handler", "");

        getCallsCounter(handlerName).increment();

        long start = System.nanoTime();
        Object result;
        try {
            result = pjp.proceed();
        } finally {
            long elapsed = System.nanoTime() - start;
            getTimer(handlerName).record(elapsed, TimeUnit.NANOSECONDS);
            log.debug("[PERF] handler='{}', elapsed={}ms", handlerName, elapsed / 1_000_000.0);
        }

        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] instanceof REFRecognitionContext ctx) {
            if (ctx.isCompleted()) {
                String completedBy = ctx.getCompletedBy();
                if (completedBy != null && completedBy.equals(handlerName)) {
                    // output 유무로 complete / reject 구분
                    if (ctx.getOutput() != null) {
                        getCompletedCounter(handlerName).increment();
                    } else {
                        getRejectedCounter(handlerName).increment();
                    }
                }
            }
        }

        return result;
    }

    // ── 전체 파이프라인 측정 (원래 코드에서 유지) ──────────
    @Around("execution(* com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService.recognize(..))")
    public Object measurePipeline(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.nanoTime() - start;
            log.debug("[PERF] 전체 파이프라인 elapsed={}ms", elapsed / 1_000_000.0);

            // 파이프라인 전체 Timer (핸들러 Timer와 별도)
            timerCache.computeIfAbsent("Pipeline", name ->
                    Timer.builder("recognition.pipeline.duration")
                            .description("전체 파이프라인 실행 시간")
                            .publishPercentiles(0.5, 0.95, 0.99)
                            .publishPercentileHistogram()
                            .register(meterRegistry)
            ).record(elapsed, TimeUnit.NANOSECONDS);
        }
    }

    // ── Meter 캐시 ──────────────────────────────────────
    private Timer getTimer(String name) {
        return timerCache.computeIfAbsent(name, n ->
                Timer.builder("recognition.handler.duration")
                        .tag("handler", n)
                        .description("파이프라인 핸들러 실행 시간")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .publishPercentileHistogram()   // 1회만
                        .register(meterRegistry)
        );
    }

    private Counter getCallsCounter(String name) {
        return callsCache.computeIfAbsent(name, n ->
                Counter.builder("recognition.handler.calls.total")
                        .tag("handler", n)
                        .description("핸들러 호출 횟수")
                        .register(meterRegistry)
        );
    }

    private Counter getCompletedCounter(String name) {
        return completedCache.computeIfAbsent(name, n ->
                Counter.builder("recognition.handler.completed.total")
                        .tag("handler", n)
                        .description("이 핸들러에서 파이프라인 완료된 횟수")
                        .register(meterRegistry)
        );
    }

    private Counter getRejectedCounter(String name) {
        return rejectedCache.computeIfAbsent(name, n ->
                Counter.builder("recognition.handler.rejected.total")
                        .tag("handler", n)
                        .description("이 핸들러에서 비식재료 반려된 횟수")
                        .register(meterRegistry)
        );
    }
}