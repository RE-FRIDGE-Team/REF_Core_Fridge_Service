package com.refridge.core_server.product_recognition.presentation;

import com.refridge.core_server.product_recognition.application.REFProductRecognitionBatchService;
import com.refridge.core_server.product_recognition.application.dto.result.REFBatchRecognitionResultResponse;
import com.refridge.core_server.product_recognition.presentation.dto.REFProductMetadataBatchRecognitionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/recognize/batch")
@RequiredArgsConstructor
public class REFProductMetadataBatchRecognitionController {

    private final REFProductRecognitionBatchService batchService;

    /**
     * 순차 Stream 방식 Batch 인식
     */
    @PostMapping("/sequential")
    public ResponseEntity<REFBatchRecognitionResultResponse> recognizeBatchSequential(
            @RequestBody REFProductMetadataBatchRecognitionRequest request) {

        log.info("[Batch 순차] 요청: {}건", request.rawProductNames().size());
        String requesterId = UUID.randomUUID().toString();

        REFBatchRecognitionResultResponse response =
                batchService.recognizeBatchSequential(request.rawProductNames(), requesterId);

        return ResponseEntity.ok(response);
    }

    /**
     * Virtual Thread 병렬 방식 Batch 인식
     */
    @PostMapping("/parallel")
    public ResponseEntity<REFBatchRecognitionResultResponse> recognizeBatchParallel(
            @RequestBody REFProductMetadataBatchRecognitionRequest request) {

        log.info("[Batch 병렬] 요청: {}건", request.rawProductNames().size());
        String requesterId = UUID.randomUUID().toString();

        REFBatchRecognitionResultResponse response =
                batchService.recognizeBatchParallel(request.rawProductNames(), requesterId);

        return ResponseEntity.ok(response);
    }
}