package com.refridge.core_server.product_recognition.application.dto.command;

public record REFRecognitionRequestCommand(
        String inputText,
        String requesterId
) {
}
