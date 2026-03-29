package com.refridge.core_server.product_recognition.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제품명 파싱 파이프라인이 산출한 결과를 영속화하는 Embedded VO입니다.
 * <p>
 * 기존에는 {@link REFParsedProductInformation}이 파이프라인 실행 중
 * {@code REFRecognitionContext}(메모리 객체)에만 존재하고 파이프라인 종료 시 소멸했습니다.
 * 이 VO를 통해 Recognition AR에 영구 저장되어 다음 용도로 활용됩니다:
 * <ul>
 *   <li>피드백 BC: 원본 스냅샷 구성 (브랜드/수량/용량 diff 판단)</li>
 *   <li>파서 성능 분석: 브랜드 추출 정확도, 정제 품질 추적</li>
 *   <li>이벤트 발행: 완료 이벤트에 파싱 결과 포함</li>
 * </ul>
 */
@Getter
@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFParsedProductResult {

    /** 파서가 정제한 제품명 (브랜드/수량/용량/노이즈 제거 후) */
    @Column(name = "parsed_product_name")
    private String refinedProductName;

    /** 파서가 추출한 브랜드명 (Aho-Corasick 사전 매칭) */
    @Column(name = "parsed_brand_name")
    private String brandName;

    /** 파서가 추출한 수량 (정규식) */
    @Column(name = "parsed_quantity")
    private Integer quantity;

    /** 파서가 추출한 용량 텍스트 e.g. "500ml", "1.5kg" */
    @Column(name = "parsed_volume")
    private String volume;

    /** 파서가 추출한 용량 단위 e.g. "ml", "g", "kg" */
    @Column(name = "parsed_volume_unit")
    private String volumeUnit;

    /**
     * 파이프라인 실행 중 메모리 객체({@link REFParsedProductInformation})로부터 영속화용 VO를 생성합니다.
     * <p>
     * 호출 시점: 파이프라인 완료 후, Recognition AR의 상태를 업데이트할 때.
     */
    public static REFParsedProductResult from(REFParsedProductInformation parsed) {
        if (parsed == null) {
            return empty();
        }

        return new REFParsedProductResult(
                parsed.refinedText(),
                parsed.brandName(),
                parsed.quantity(),
                parsed.volume(),
                parsed.volumeUnit()
        );
    }

    /** 파싱 실패 또는 파서를 거치지 않은 경우 */
    public static REFParsedProductResult empty() {
        return new REFParsedProductResult(null, null, null, null, null);
    }
}