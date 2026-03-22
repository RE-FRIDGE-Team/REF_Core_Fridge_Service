package com.refridge.core_server.recognition_feedback.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum REFFeedbackStatus {

    /** 인식 완료 직후 자동 생성 — 사용자 응답 대기 */
    PENDING("P", "확인 대기"),

    /** 사용자가 인식 결과를 그대로 승인 (긍정 피드백), 만료로 인한 긍정 피드백 */
    APPROVED("A", "승인됨"),

    /** 사용자가 인식 결과를 수정 (부정 피드백) */
    CORRECTED("C", "수정됨");

    private final String dbCode;
    private final String korCode;

    public static REFFeedbackStatus fromDbCode(String dbCode) {
        return Arrays.stream(REFFeedbackStatus.values())
                .filter(s -> s.dbCode.equals(dbCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않은 피드백 상태 코드입니다: " + dbCode));
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == CORRECTED;
    }
}