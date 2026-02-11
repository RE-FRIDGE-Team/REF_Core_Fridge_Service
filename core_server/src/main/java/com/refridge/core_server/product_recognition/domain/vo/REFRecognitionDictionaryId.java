package com.refridge.core_server.product_recognition.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Embeddable
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class REFRecognitionDictionaryId {

    @Column(name = "dict_id")
    private UUID value;

    public static REFRecognitionDictionaryId generate() {
        return new REFRecognitionDictionaryId(UUID.randomUUID());
    }

    public static REFRecognitionDictionaryId of(UUID value) {
        return new REFRecognitionDictionaryId(value);
    }
}
