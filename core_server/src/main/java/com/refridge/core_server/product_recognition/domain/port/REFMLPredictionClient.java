package com.refridge.core_server.product_recognition.domain.port;

import com.refridge.core_server.product_recognition.domain.dto.REFMLPredictionInfo;

import java.util.Optional;

public interface REFMLPredictionClient {

    Optional<REFMLPredictionInfo> predict(String input);

}
