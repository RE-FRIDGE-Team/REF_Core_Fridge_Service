package com.refridge.core_server.product_recognition.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record REFProductMetadataBatchRecognitionRequest(
        @JsonProperty("raw_product_names")
        List<String> rawProductNames
) {
}
