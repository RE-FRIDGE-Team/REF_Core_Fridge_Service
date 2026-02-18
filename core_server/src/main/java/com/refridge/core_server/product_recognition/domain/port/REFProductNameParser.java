package com.refridge.core_server.product_recognition.domain.port;

import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductName;

/**
 * 제품명 파싱 포트.
 * 원본 제품명에서 브랜드명, 수량, 용량, 정제된 제품명을 추출한다.
 */
public interface REFProductNameParser {

    REFParsedProductName parse(String rawProductName);

}
