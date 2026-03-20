package com.refridge.core_server.product_recognition.application;

import com.refridge.core_server.product_recognition.application.dto.command.REFRecognitionRequestCommand;
import com.refridge.core_server.product_recognition.application.dto.result.REFBatchRecognitionResultResponse;
import com.refridge.core_server.product_recognition.application.dto.result.REFRecognitionResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
@RequiredArgsConstructor
public class REFProductRecognitionBatchService {

    private final REFProductRecognitionAppService recognitionAppService;

    /**
     * 순차 Stream 방식.
     * 각 제품명을 순서대로 파이프라인에 통과시킨다.
     */
    public REFBatchRecognitionResultResponse recognizeBatchSequential(
            List<String> rawProductNames, String requesterId) {

        long start = System.currentTimeMillis();

        List<REFRecognitionResultResponse> results = rawProductNames.stream()
                .map(name -> recognizeSafely(name, requesterId))
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        log.info("[Batch 순차] {}건 처리 완료. elapsed={}ms", results.size(), elapsed);

        return REFBatchRecognitionResultResponse.of(results, elapsed);
    }

    /**
     * Virtual Thread 병렬 방식.
     * StructuredTaskScope로 각 제품명을 Virtual Thread에서 동시 실행한다.
     * JDK 21 정식 API (JEP 453).
     */
    public REFBatchRecognitionResultResponse recognizeBatchParallel(
            List<String> rawProductNames, String requesterId) {

        long start = System.currentTimeMillis();

        List<REFRecognitionResultResponse> results;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<REFRecognitionResultResponse>> futures = rawProductNames.stream()
                    .map(name -> executor.submit(() -> recognizeSafely(name, requesterId)))
                    .toList();

            results = futures.stream()
                    .map(f -> {
                        try { return f.get(); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    })
                    .toList();
        }

        long elapsed = System.currentTimeMillis() - start;
        return REFBatchRecognitionResultResponse.of(results, elapsed);
    }

    /**
     * 단건 인식을 안전하게 래핑.
     * 한 건 실패가 전체 배치를 중단시키지 않도록 한다.
     */
    private REFRecognitionResultResponse recognizeSafely(String rawProductName, String requesterId) {
        try {
            return recognitionAppService.recognize(
                    new REFRecognitionRequestCommand(rawProductName, requesterId)
            );
        } catch (Exception e) {
            log.error("[Batch] 단건 인식 실패: '{}', 사유: {}", rawProductName, e.getMessage());
            return REFRecognitionResultResponse.failure(rawProductName, e.getMessage());
        }
    }
}