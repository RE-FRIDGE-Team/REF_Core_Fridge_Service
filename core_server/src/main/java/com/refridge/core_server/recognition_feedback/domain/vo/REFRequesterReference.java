package com.refridge.core_server.recognition_feedback.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * 피드백을 생성한 요청자의 ID를 보관하는 Value Object입니다.
 * <p>
 * {@code product_recognition} 컨텍스트의 {@code REFRequesterId}를 직접 참조하지 않고,
 * 피드백 BC 내부에서 독립적으로 요청자를 식별합니다.
 */
@Getter
@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRequesterReference {

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    public static REFRequesterReference of(UUID requesterId) {
        Objects.requireNonNull(requesterId, "요청자 ID는 필수입니다.");
        return new REFRequesterReference(requesterId);
    }

    public static REFRequesterReference fromString(String requesterId) {
        return of(UUID.fromString(requesterId));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof REFRequesterReference other)) return false;
        return Objects.equals(requesterId, other.requesterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requesterId);
    }
}