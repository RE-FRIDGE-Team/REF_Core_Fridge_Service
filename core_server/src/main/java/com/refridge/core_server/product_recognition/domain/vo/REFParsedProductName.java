package com.refridge.core_server.product_recognition.domain.vo;

import lombok.Builder;

import java.util.Optional;


/**
 * 제품명 파싱 결과 Value Object
 * Parser가 원본 제품명에서 추출한 구조화된 정보를 담는다.
 */
@Builder
public record REFParsedProductName(
        String originalText,      // 원본 제품명
        String refinedText,       // 정제된 제품명 (수량/용량/브랜드/불필요요소 제거)
        String brandName,         // 추출된 브랜드명 (없으면 null)
        Integer quantity,         // 추출된 수량 (없으면 null)
        String volume,            // 추출된 용량 e.g. "500ml", "1.5kg" (없으면 null)
        String volumeUnit         // 용량 단위 e.g. "ml", "g", "kg" (없으면 null)
) {
    public Optional<String> getBrandName() {
        return Optional.ofNullable(brandName);
    }

    public Optional<Integer> getQuantity() {
        return Optional.ofNullable(quantity);
    }

    public Optional<String> getVolume() {
        return Optional.ofNullable(volume);
    }
}