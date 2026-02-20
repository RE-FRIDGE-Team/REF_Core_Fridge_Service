package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.product_recognition.domain.dto.REFProductIndexSearchInfo;
import com.refridge.core_server.product_recognition.domain.port.REFProductIndexSearcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductQueryAdapter implements REFProductIndexSearcher {

    private final REFProductRepository productRepository;

    @Override
    public Optional<REFProductIndexSearchInfo> search(String input) {
        return Optional.empty();
    }
}
