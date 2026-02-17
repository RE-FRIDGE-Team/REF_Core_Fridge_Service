package com.refridge.core_server.product_recognition.domain.service;

import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.port.REFBrandMatcher;
import com.refridge.core_server.product_recognition.domain.port.REFExclusionWordMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class REFProductRecognitionDomainService {

    private final REFBrandMatcher brandMatcher;

    private final REFExclusionWordMatcher exclusionWordMatcher;

    public void executeRecognitionPipeline(REFProductRecognition recognition, String inputText){

    }

}
