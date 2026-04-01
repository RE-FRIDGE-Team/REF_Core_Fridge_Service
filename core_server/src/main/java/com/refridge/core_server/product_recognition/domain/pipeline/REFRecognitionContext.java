package com.refridge.core_server.product_recognition.domain.pipeline;


import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductInformation;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.Getter;
import lombok.Setter;

/**
 * Recognition 파이프라인 전반에 걸쳐 공유되는 컨텍스트.
 * 각 핸들러는 이 객체를 읽고/쓰며 다음 핸들러에 상태를 전달한다.
 * 불변 객체가 아닌 이유: 파이프라인 단계별로 상태가 점진적으로 채워지는 구조이기 때문.
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

    /**
     * alias 교체 여부.
     * true이면 refinedText가 alias로 교체된 상태입니다.
     * correctionSuggestions 조회를 생략하는 데 사용됩니다.
     */
    private boolean aliasApplied = false;

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

    /**
     * alias 교체가 적용되었음을 표시합니다.
     * REFProductNameParsingHandler에서 alias 교체 후 호출합니다.
     */
    public void markAliasApplied() {
        this.aliasApplied = true;
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