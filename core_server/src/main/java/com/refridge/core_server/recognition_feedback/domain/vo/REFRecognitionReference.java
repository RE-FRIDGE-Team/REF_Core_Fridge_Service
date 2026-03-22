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
 * 피드백이 참조하는 인식 결과의 ID를 보관하는 Value Object입니다.
 * <p>
 * {@code product_recognition} 컨텍스트의 {@code REFRecognitionId}를 직접 참조하지 않고,
 * 피드백 BC 내부에서 독립적으로 인식 결과를 식별합니다.
 * Anti-Corruption Layer 역할 — Bounded Context 간 컴파일 타임 의존을 제거합니다.
 */
@Getter
@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRecognitionReference {

    @Column(name = "recognition_id", nullable = false)
    private UUID recognitionId;

    public static REFRecognitionReference of(UUID recognitionId) {
        Objects.requireNonNull(recognitionId, "인식 결과 ID는 필수입니다.");
        return new REFRecognitionReference(recognitionId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof REFRecognitionReference other)) return false;
        return Objects.equals(recognitionId, other.recognitionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recognitionId);
    }
}