package com.refridge.core_server.product.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;


@Getter
@AllArgsConstructor
public enum REFProductStatus {
    ACTIVE("A", "활성"),
    DELETED("D", "삭제됨"),
    PENDING_REVIEW("P", "검수대기");

    private final String code;
    private final String description;

    public static REFProductStatus fromCode(String code) {
        return Arrays.stream(REFProductStatus.values())
                .filter(status -> status.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "잘못된 제품 상태 코드입니다: " + code
                ));
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isDeleted() {
        return this == DELETED;
    }

    public boolean isPendingReview() {
        return this == PENDING_REVIEW;
    }
}
















