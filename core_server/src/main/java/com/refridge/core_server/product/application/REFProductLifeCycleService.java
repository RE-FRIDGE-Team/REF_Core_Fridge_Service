package com.refridge.core_server.product.application;

import com.refridge.core_server.product.domain.REFProductRepository;
import com.refridge.core_server.product.domain.ar.REFProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class REFProductLifeCycleService {

    private final REFProductRepository productRepository;

    /**
     * 원제품명 + groceryItemId 조합이 이미 존재하면 SKIP, 없으면 생성.
     * CSV 부트스트랩 전용.
     */
    @Transactional
    public void upsertProduct(
            String originalProductName,
            String brandName,
            Long groceryItemId,
            Long majorCategoryId,
            Long minorCategoryId
    ) {
        boolean exists = productRepository
                .existsByProductNameValueAndGroceryItemId(
                        originalProductName, groceryItemId);

        if (exists) return;

        productRepository.save(
                REFProduct.create(
                        originalProductName,
                        brandName,
                        groceryItemId,
                        majorCategoryId,
                        minorCategoryId
                )
        );
    }
}
