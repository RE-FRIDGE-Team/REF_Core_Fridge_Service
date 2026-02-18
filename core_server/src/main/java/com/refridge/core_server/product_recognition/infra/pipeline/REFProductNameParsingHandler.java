package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFProductNameParser;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 제품명 파싱 핸들러.
 * 이 핸들러는 완료 처리를 하지 않는다.
 * 파싱 결과를 context에 저장하고 다음 핸들러(사전 매칭, 색인 검색 등)가 사용할 수 있도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameParsingHandler implements REFRecognitionHandler{

    private final REFProductNameParser productNameParser;

    @Override
    public void handle(REFRecognitionContext context) {
        REFParsedProductName parsed = productNameParser.parse(context.getRawInput());
        context.setParsedProductName(parsed);

        log.debug("제품명 파싱 완료. original='{}', refined='{}', brand='{}', qty={}, vol='{}'",
                parsed.originalText(),
                parsed.refinedText(),
                parsed.getBrandName().orElse("-"),
                parsed.getQuantity().orElse(null),
                parsed.getVolume().orElse("-")
        );
    }

    @Override
    public String handlerName() {
        return "ProductNameParsing";
    }
}
