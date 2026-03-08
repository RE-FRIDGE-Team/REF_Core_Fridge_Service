package com.refridge.core_server.bootstrap.strategy;

import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;

/***
 * GOF 전략 패턴의 Strategy 인터페이스 역할을 하는 인터페이스입니다.
 * REFRecognitionDictionary의 초기화 전략을 정의합니다.
 */
public interface REFDictionaryInitializationStrategy {

    /**
     * 이 전략이 처리할 수 있는 사전 타입인지 확인합니다.
     */
    boolean supports(REFRecognitionDictionaryType type);

    /**
     * 신규 사전 생성 시 초기 데이터를 채웁니다.
     */
    void initializeEntries(REFRecognitionDictionary dictionary);

    /**
     * 기존 사전에 누락된 항목을 보완합니다.
     */
    void supplementMissingEntries(REFRecognitionDictionary dictionary);

}
