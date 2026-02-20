package com.refridge.core_server.product.domain;

import com.refridge.core_server.product.infra.dto.REFProductSearchResultDto;

import java.util.List;
import java.util.Optional;

public interface REFProductRepositoryCustom {

    /**
     * 제품명 기반 검색 (매칭 우선순위 적용)
     *
     * @param productName 정제된 제품명
     * @param brandName 브랜드명 (optional)
     * @return 매칭된 제품 정보 (최상위 매칭 1건)
     */
    Optional<REFProductSearchResultDto> searchByProductName(String productName, String brandName);

    /**
     * 제품명 기반 다중 검색 (유사도 순 정렬)
     *
     * @param productName 정제된 제품명
     * @param limit 결과 개수 제한
     * @return 유사도 순으로 정렬된 제품 목록
     */
    List<REFProductSearchResultDto> searchMultipleByProductName(String productName, int limit);
}
