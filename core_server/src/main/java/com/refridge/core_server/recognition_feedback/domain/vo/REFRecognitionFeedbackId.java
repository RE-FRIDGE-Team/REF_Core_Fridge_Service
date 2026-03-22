package com.refridge.core_server.recognition_feedback.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRecognitionFeedbackId implements Serializable {

    @Column(name = "feedback_id")
    private UUID value;

    public static REFRecognitionFeedbackId generate() {
        return new REFRecognitionFeedbackId(UUID.randomUUID());
    }

    public static REFRecognitionFeedbackId of(UUID value) {
        return new REFRecognitionFeedbackId(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof REFRecognitionFeedbackId other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}