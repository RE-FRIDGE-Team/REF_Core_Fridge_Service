package com.refridge.core_server.product_recognition.domain.port;

import com.refridge.core_server.product_recognition.domain.dto.REFProductIndexSearchInfo;

import java.util.Optional;

public interface REFProductIndexSearcher {

    Optional<REFProductIndexSearchInfo> search(String input);

}
