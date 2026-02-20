package com.refridge.core_server.product_recognition.domain.pipeline;


import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductName;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

/**
 * Recognition 파이프라인 전반에 걸쳐 공유되는 컨텍스트.
 * 각 핸들러는 이 객체를 읽고/쓰며 다음 핸들러에 상태를 전달한다.
 * 불변 객체가 아닌 이유: 파이프라인 단계별로 상태가 점진적으로 채워지는 구조이기 때문.
 */
@Getter
public class REFRecognitionContext {

    // 원본 입력 (불변)
    private final String rawInput;
    private final REFProductRecognition recognition;

    // 파이프라인 실행 중 채워지는 필드
    @Setter
    private REFParsedProductName parsedProductName;
    private REFProductRecognitionOutput output;
    private boolean completed = false;
    private String completedBy; // 어느 단계에서 완료됐는지 (디버깅/로깅용)

    public REFRecognitionContext(String rawInput, REFProductRecognition recognition) {
        this.rawInput = rawInput;
        this.recognition = recognition;
    }

    /**
     * 파이프라인을 완료 상태로 마킹한다.
     * 이후 핸들러들은 isCompleted()를 확인하고 처리를 건너뛴다.
     */
    public void complete(REFProductRecognitionOutput output, String completedBy) {
        this.output = output;
        this.completed = true;
        this.completedBy = completedBy;
    }

    /**
     * 반려 (비식재료) 처리
     */
    public void reject(String completedBy) {
        this.completed = true;
        this.completedBy = completedBy;
        this.output = null;
    }

    /**
     * 정제된 제품명 반환. 파싱 이전 단계에서 호출 시 원본 텍스트 반환.
     */
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
