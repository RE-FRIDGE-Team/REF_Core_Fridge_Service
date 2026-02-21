package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.product_recognition.domain.dto.REFMLPredictionInfo;
import com.refridge.core_server.product_recognition.domain.port.REFMLPredictionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFMLPredictionAdapter implements REFMLPredictionClient {

    // TODO : WebClient 를 이용하여 ML Prediction API와 통신하는 로직 구현
    @Override
    public Optional<REFMLPredictionInfo> predict(String input) {
        return Optional.empty();
    }
}
