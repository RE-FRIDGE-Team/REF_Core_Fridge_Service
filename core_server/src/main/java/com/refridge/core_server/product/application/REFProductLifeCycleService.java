package com.refridge.core_server.product.application;

import com.refridge.core_server.product.domain.REFProductRepository;
import com.refridge.core_server.product.domain.ar.REFProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class REFProductLifeCycleService {

    private final REFProductRepository productRepository;

    /**
     * 제품명 기준으로 Product를 upsert합니다.
     *
     * <h3>변경 사항 (2026. 4. 14.)</h3>
     * <p>
     * 기존에는 {@code (productName, groceryItemId)} 조합으로 중복 체크했습니다.
     * 이 경우 식재료 교정으로 인해 같은 제품명에 다른 GroceryItem으로 매핑된
     * Product가 중복 생성될 수 있습니다. 이후 {@code ProductIndexSearch}에서
     * 두 결과 중 어느 것이 반환될지 비결정적인 문제가 있었습니다.
     * </p>
     *
     * <h3>변경 후 동작</h3>
     * <ul>
     *   <li>동일 제품명의 ACTIVE Product가 없으면 새로 생성합니다.</li>
     *   <li>동일 제품명의 ACTIVE Product가 있고 GroceryItem이 같으면 스킵합니다.</li>
     *   <li>동일 제품명의 ACTIVE Product가 있고 GroceryItem이 다르면
     *       {@code updateGroceryItemReference()}로 업데이트합니다.
     *       (식재료 교정 반영)</li>
     * </ul>
     *
     * <h3>호출 경로</h3>
     * <pre>
     *   1. 긍정 피드백 집계 → REFProductFeedbackAggregationEventHandler
     *   2. 식재료명 교정 확정 → REFProductRegistrationByGroceryItemCorrectionHandler
     *   3. 카테고리 재분류 승인 → REFProductCategoryUpdateByReassignmentHandler
     *   4. CSV 부트스트랩
     * </pre>
     */
    @Transactional
    public void upsertProduct(
            String originalProductName,
            String brandName,
            Long groceryItemId,
            Long majorCategoryId,
            Long minorCategoryId
    ) {
        productRepository.findActiveByProductNameValue(originalProductName)
                .ifPresentOrElse(
                        existing -> {
                            Long currentGroceryItemId =
                                    existing.getGroceryItemReference().getGroceryItemId();

                            if (currentGroceryItemId.equals(groceryItemId)) {
                                // 동일 GroceryItem — 스킵
                                log.debug("[Product upsert] 이미 동일 매핑 존재. " +
                                                "productName='{}', groceryItemId={}",
                                        originalProductName, groceryItemId);
                                return;
                            }

                            // GroceryItem이 달라졌으면 업데이트 (식재료 교정 반영)
                            existing.updateGroceryItemReference(
                                    groceryItemId, majorCategoryId, minorCategoryId);
                            productRepository.save(existing);

                            log.info("[Product upsert] GroceryItem 업데이트. " +
                                            "productName='{}', {} → {}",
                                    originalProductName, currentGroceryItemId, groceryItemId);
                        },
                        () -> {
                            // 신규 생성
                            productRepository.save(
                                    REFProduct.create(
                                            originalProductName, brandName,
                                            groceryItemId, majorCategoryId, minorCategoryId
                                    )
                            );
                            log.info("[Product upsert] 신규 생성. productName='{}', groceryItemId={}",
                                    originalProductName, groceryItemId);
                        }
                );
    }
}