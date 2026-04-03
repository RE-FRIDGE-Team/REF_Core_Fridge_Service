package com.refridge.core_server.product_recognition.domain.pipeline;

import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.Getter;
import lombok.Setter;

/**
 * Recognition 파이프라인 전반에 걸쳐 공유되는 컨텍스트.
 * 각 핸들러는 이 객체를 읽고/쓰며 다음 핸들러에 상태를 전달한다.
 *
 * <h3>alias 관련 필드 제거 이유</h3>
 * alias 교체는 파이프라인 내부가 아닌 AppService에서 응답 수준에서 수행합니다.
 * 따라서 aliasApplied 플래그는 Context가 아닌
 * {@link com.refridge.core_server.product_recognition.application.dto.result.REFCachedPipelineResult}에서
 * 관리합니다.
 */
@Getter
public class REFRecognitionContext {

    private final String rawInput;
    private final REFProductRecognition recognition;

    @Setter
    private REFParsedProductInformation parsedProductName;
    private REFProductRecognitionOutput output;
    private boolean completed = false;
    private String completedBy;

    public REFRecognitionContext(String rawInput, REFProductRecognition recognition) {
        this.rawInput = rawInput;
        this.recognition = recognition;
    }

    public void complete(REFProductRecognitionOutput output, String completedBy) {
        this.output = output;
        this.completed = true;
        this.completedBy = completedBy;
    }

    public void reject(String completedBy) {
        this.completed = true;
        this.completedBy = completedBy;
        this.output = null;
    }

    public String getEffectiveInput() {
        if (parsedProductName != null) {
            return parsedProductName.refinedText();
        }
        return rawInput;
    }

    public String getParsedBrandName() {
        return this.parsedProductName.brandName();
    }
}