package com.refridge.core_server.product_recognition.infra.aop;


import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Profile("perf")
@RequiredArgsConstructor
public class REFPipelinePerformanceAspect {

    private final MeterRegistry meterRegistry;

    /**
     * 파이프라인 핸들러 단계별 측정
     * execution 포인트컷: 해당 인터페이스의 handle() 구현체 전부를 가로챔
     */
    @Around("execution(* com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler.handle(..))")
    public Object measureHandler(ProceedingJoinPoint pjp) throws Throwable {
        String handlerName = pjp.getTarget().getClass().getSimpleName();
        long start = System.nanoTime();

        try {
            return pjp.proceed(); // ← 실제 handle() 실행
        } finally {
            // 예외가 나도 반드시 측정
            long elapsed = System.nanoTime() - start;
            log.info("[PERF] handler='{}', elapsed={}ms",
                    handlerName, elapsed / 1_000_000.0);

            // Micrometer에도 기록 (Prometheus 연동)
            Timer.builder("recognition.handler.duration")
                    .tag("handler", handlerName)
                    .register(meterRegistry)
                    .record(elapsed, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 전체 파이프라인 측정
     */
    @Around("execution(* com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService.recognize(..))")
    public Object measurePipeline(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();

        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.nanoTime() - start;
            log.info("[PERF] 전체 파이프라인 elapsed={}ms", elapsed / 1_000_000.0);

            Timer.builder("recognition.pipeline.duration")
                    .register(meterRegistry)
                    .record(elapsed, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

}
