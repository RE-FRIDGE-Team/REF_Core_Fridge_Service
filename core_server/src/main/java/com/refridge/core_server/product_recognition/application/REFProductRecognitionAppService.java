package com.refridge.core_server.product_recognition.application;

import com.refridge.core_server.product_recognition.domain.REFProductRecognitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class REFProductRecognitionAppService {

    private final REFProductRecognitionRepository productRecognitionRepository;

}
