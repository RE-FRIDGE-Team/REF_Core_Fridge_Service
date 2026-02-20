package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.product.domain.REFProductRepository;
import com.refridge.core_server.product_recognition.domain.dto.REFProductIndexSearchInfo;
import com.refridge.core_server.product_recognition.domain.port.REFProductIndexSearcher;
import com.refridge.core_server.product_recognition.infra.mapper.REFProductSearchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductQueryAdapter implements REFProductIndexSearcher {

    private final REFProductRepository productRepository;
    private final REFProductSearchMapper mapper;

    @Override
    public Optional<REFProductIndexSearchInfo> search(String input, String brandName) {
        if (input == null || input.isBlank()) {
            log.warn("search 호출 시 input이 null 또는 빈 문자열입니다.");
            return Optional.empty();
        }

        return productRepository.searchByProductName(input, brandName)
                .map(mapper::toSearchInfo);
    }
}
