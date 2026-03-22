package com.refridge.core_server.recognition_feedback.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 피드백의 상태를 나타내는 열거형입니다.
 * <pre>
 *   PENDING ──→ APPROVED   (사용자 승인 또는 미응답 자동 승인)
 *          └──→ CORRECTED  (사용자가 인식 결과 수정)
 * </pre>
 * 미응답 시에도 APPROVED로 처리합니다 — 사용자가 별도 수정 없이 넘어간 경우
 * 인식 결과에 이의가 없다고 판단하는 것이 자연스럽기 때문입니다.
 */
@Getter
@RequiredArgsConstructor
public enum REFFeedbackStatus {

    /** 인식 완료 직후 자동 생성 — 사용자 응답 대기 */
    PENDING("P", "확인 대기"),

    /** 사용자가 인식 결과를 그대로 승인하거나, 미응답으로 자동 승인됨 (긍정 피드백) */
    APPROVED("A", "승인됨"),

    /** 사용자가 인식 결과를 수정함 (부정 피드백) */
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