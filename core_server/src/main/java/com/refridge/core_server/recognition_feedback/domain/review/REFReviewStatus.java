package com.refridge.core_server.recognition_feedback.domain.review;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum REFReviewStatus {

    /** 관리자 검수 대기 */
    PENDING("P", "검수 대기"),

    /** 관리자가 승인하여 반영 완료 */
    APPROVED("A", "승인 반영됨"),

    /** 관리자가 거부 (반영하지 않음) */
    REJECTED("R", "거부됨");

    private final String dbCode;
    private final String korCode;

    public static REFReviewStatus fromDbCode(String dbCode) {
        return Arrays.stream(REFReviewStatus.values())
                .filter(s -> s.dbCode.equals(dbCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않은 검수 상태 코드입니다: " + dbCode));
    }

    public boolean isPending() {
        return this == PENDING;
    }
}