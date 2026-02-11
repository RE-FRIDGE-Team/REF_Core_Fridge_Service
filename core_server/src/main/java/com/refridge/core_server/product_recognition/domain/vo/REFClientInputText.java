package com.refridge.core_server.product_recognition.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Embeddable
@Builder
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class REFClientInputText {

    @Column(name = "input_text", nullable = false, length = 1000)
    private String inputText;

    public static REFClientInputText of(String inputText) {
        return new REFClientInputText(inputText);
    }
}
