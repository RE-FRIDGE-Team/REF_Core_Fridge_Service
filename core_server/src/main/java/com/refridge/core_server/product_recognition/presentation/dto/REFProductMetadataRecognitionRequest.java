package com.refridge.core_server.product_recognition.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record REFProductMetadataRecognitionRequest (
        @JsonProperty("raw_product_name")
        String rawProductName
) {
}
