package com.refridge.core_server.product_recognition.presentation;

import com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService;
import com.refridge.core_server.product_recognition.application.dto.result.REFRecognitionResultResponse;
import com.refridge.core_server.product_recognition.presentation.dto.REFProductMetadataRecognitionRequest;
import com.refridge.core_server.product_recognition.presentation.mapper.REFProductMetadataRecognitionRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class REFProductMetadataRecognitionController {

    private final REFProductRecognitionAppService productRecognitionAppService;
    private final REFProductMetadataRecognitionRequestMapper productMetadataRecognitionRequestMapper;

    @GetMapping("/recognize")
    public void recognizeProductName(@RequestBody REFProductMetadataRecognitionRequest request) {
        log.info("상품명 인식 요청: input='{}'", request.rawProductName());
        REFRecognitionResultResponse recognizedResult =
                productRecognitionAppService.recognize(productMetadataRecognitionRequestMapper.toCommand(request));
        log.info("인식 결과: '{}'", recognizedResult.toString());
    }
}
