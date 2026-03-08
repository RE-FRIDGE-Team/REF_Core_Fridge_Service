package com.refridge.core_server.product_recognition.presentation.mapper;

import com.refridge.core_server.product_recognition.application.dto.command.REFRecognitionRequestCommand;
import com.refridge.core_server.product_recognition.presentation.dto.REFProductMetadataRecognitionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface REFProductMetadataRecognitionRequestMapper  {

    @Mapping(source = "rawProductName", target = "inputText")
    @Mapping(target = "requesterId", expression = "java(java.util.UUID.randomUUID().toString())")
    REFRecognitionRequestCommand toCommand(REFProductMetadataRecognitionRequest request);
}
