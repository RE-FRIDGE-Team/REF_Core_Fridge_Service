package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFProductNameParser;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 제품명 파싱 핸들러.
 * 완료 처리를 하지 않으며, 파싱 결과를 context에 저장합니다.
 *
 * <h3>alias 교체 미수행 이유</h3>
 * alias 교체는 이 핸들러에서 수행하지 않습니다.
 * 파이프라인 중간에 alias로 refinedText를 교체하면,
 * 이미 "원본 제품명"으로 등록된 Product를 ProductIndexSearcher가 찾지 못하는
 * 역설적 상황이 발생합니다.
 *
 * alias 교체는 파이프라인이 완전히 완료된 이후
 * {@link com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService}에서
 * 응답 수준에서만 수행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameParsingHandler implements REFRecognitionHandler {

    private final REFProductNameParser productNameParser;

    @Override
    public void handle(REFRecognitionContext context) {
        REFParsedProductInformation parsed = productNameParser.parse(context.getRawInput());
        context.setParsedProductName(parsed);

        log.debug("[파싱 핸들러] 완료. original='{}', refined='{}', brand='{}', qty={}, vol='{}'",
                parsed.originalText(),
                parsed.refinedText(),
                parsed.getBrandName().orElse("-"),
                parsed.getQuantity().orElse(null),
                parsed.getVolume().orElse("-"));
    }

    @Override
    public String handlerName() {
        return "ProductNameParsing";
    }
}