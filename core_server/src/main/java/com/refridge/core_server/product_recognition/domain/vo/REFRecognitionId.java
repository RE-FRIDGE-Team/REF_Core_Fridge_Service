package com.refridge.core_server.product_recognition.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Embeddable
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class REFRecognitionId {

    @Column(name = "recognition_id")
    private UUID value;

    public static REFRecognitionId generate() {
        return new REFRecognitionId(UUID.randomUUID());
    }

    public static REFRecognitionId of(UUID value) {
        return new REFRecognitionId(value);
    }
}
