package com.refridge.core_server.product_recognition.infra.pipeline;

import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionHandler;
import com.refridge.core_server.product_recognition.domain.port.REFProductNameParser;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 제품명 파싱 핸들러.<p>
 * 완료 처리를 하지 않으며, 파싱 결과를 context에 저장합니다.<p>
 * <pre>
 * alias 조회 단계 (파싱 완료 후):
 *   REFAliasConfirmationService를 통해 Redis alias:confirmed Hash를 조회합니다.
 *   CONFIRMED alias가 존재하면 refinedText를 alias로 교체하고
 *   REFRecognitionContext.aliasApplied = true 로 마킹합니다.
 *
 *   aliasApplied = true 이면 AppService에서 correctionSuggestions 조회를 생략합니다.
 *   이유: alias 확정 = 해당 인식 결과가 이미 검증된 올바른 값이므로
 *         사용자에게 추가 수정 선택지를 노출할 필요가 없습니다.</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameParsingHandler implements REFRecognitionHandler {

    private final REFProductNameParser productNameParser;
    private final REFAliasConfirmationService aliasConfirmationService;

    @Override
    public void handle(REFRecognitionContext context) {
        // 1. 파싱
        REFParsedProductInformation parsed = productNameParser.parse(context.getRawInput());

        // 2. alias 조회 및 교체
        REFParsedProductInformation finalParsed = applyAliasIfPresent(parsed, context);

        context.setParsedProductName(finalParsed);

        log.debug("[파싱 핸들러] 완료. original='{}', refined='{}', brand='{}', aliasApplied={}",
                finalParsed.originalText(),
                finalParsed.refinedText(),
                finalParsed.getBrandName().orElse("-"),
                context.isAliasApplied());
    }

    /**
     * CONFIRMED alias가 있으면 refinedText를 교체한 새 파싱 결과를 반환합니다.
     * alias가 없으면 원본을 그대로 반환합니다.
     */
    private REFParsedProductInformation applyAliasIfPresent(
            REFParsedProductInformation parsed,
            REFRecognitionContext context) {

        String refinedText = parsed.refinedText();
        if (refinedText == null || refinedText.isBlank()) {
            return parsed;
        }

        Optional<String> aliasOpt = aliasConfirmationService.findConfirmedAlias(refinedText);
        if (aliasOpt.isEmpty()) {
            return parsed;
        }

        String aliasName = aliasOpt.get();

        // alias 교체 마킹 - AppService에서 correctionSuggestions 조회 생략
        context.markAliasApplied();

        log.info("[파싱 핸들러] alias 교체. '{}' -> '{}', rawInput='{}'",
                refinedText, aliasName, context.getRawInput());

        // refinedText만 alias로 교체한 새 인스턴스 반환 (나머지 파싱 결과는 유지)
        return REFParsedProductInformation.builder()
                .originalText(parsed.originalText())
                .refinedText(aliasName)
                .brandName(parsed.brandName())
                .quantity(parsed.quantity())
                .volume(parsed.volume())
                .volumeUnit(parsed.volumeUnit())
                .build();
    }

    @Override
    public String handlerName() {
        return "ProductNameParsing";
    }
}