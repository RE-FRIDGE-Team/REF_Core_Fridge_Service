package com.refridge.core_server.product_recognition.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum REFEntrySource {

    /* 관리자가 직접 등록 (초기 데이터, API로 벌크 등록) */
    ADMIN("ADMIN", "관리자 생성"),

    /* 사용자 피드백으로 추가됨 */
    USER_FEEDBACK("USER", "유저 생성"),

    /* ML 모델 예측 결과가 긍정 피드백 받아서 등록됨 */
    ML_GENERATED("ML", "머신러닝 생성");

    private final String dbCode;
    private final String korCode;

    public static REFEntrySource fromDbCode(String dbData) {
        return Arrays.stream(REFEntrySource.values())
                .filter(v -> v.dbCode.equals(dbData))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Invalid dbCode for REFEntrySource: " + dbData));
    }
}
