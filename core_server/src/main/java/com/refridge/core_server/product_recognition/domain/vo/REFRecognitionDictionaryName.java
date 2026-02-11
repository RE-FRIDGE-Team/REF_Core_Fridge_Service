package com.refridge.core_server.product_recognition.domain.vo;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;


@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRecognitionDictionaryName {

    @Column(name = "dict_name")
    private String value;

    public static REFRecognitionDictionaryName of(String value) {
        return new REFRecognitionDictionaryName(value);
    }
}
