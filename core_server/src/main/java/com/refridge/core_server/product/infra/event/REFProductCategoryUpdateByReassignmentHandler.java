package com.refridge.core_server.product.infra.event;

import com.refridge.core_server.groceryItem.domain.event.REFGroceryItemCategoryChangedEvent;
import com.refridge.core_server.product.application.REFProductLifeCycleService;
import com.refridge.core_server.product.domain.REFProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GroceryItem BC가 발행한 카테고리 변경 완료 이벤트를 Product BC에서 구독하는 핸들러입니다.
 *
 * <h3>BC 경계 준수</h3>
 * <p>
 * 이 핸들러는 Product BC가 자신의 영역에서
 * GroceryItem BC 이벤트를 구독하여 Product 갱신을 처리합니다.
 * </p>
 *
 * <h3>처리 분기</h3>
 * <pre>
 *   origProductName + groceryItemId 기준으로 Product 존재 확인
 *     ├── 존재 O → updateCategoryReference()
 *     │             비정규화된 majorCategoryId, minorCategoryId 갱신
 *     └── 존재 X → upsertProduct()
 *                   다음 인식 시 ProductIndexSearch에서 우선 매칭
 * </pre>
 *
 * <h3>왜 Product도 갱신하는가</h3>
 * <p>
 * {@code REFProduct}는 {@code REFGroceryItemReference}에
 * {@code majorCategoryId}, {@code minorCategoryId}를 비정규화합니다.
 * GroceryItem의 카테고리가 변경되어도 Product의 비정규화 필드는 자동으로 갱신되지 않으므로
 * 명시적으로 업데이트합니다.
 * </p>
 *
 * <h3>Product가 없는 경우 신규 생성 이유</h3>
 * <p>
 * 파이프라인 순서가 {@code ProductIndexSearch → GroceryItemDict → ML}이므로,
 * 수정된 카테고리로 Product를 미리 등록해두면 다음 인식 시 ProductIndexSearch에서
 * GroceryItemDict/ML 이전에 정확하게 매칭됩니다.
 * </p>
 *
 * <h3>트랜잭션</h3>
 * <p>
 * {@code @Async} 메서드는 호출자의 트랜잭션을 상속받지 않습니다.
 * {@code @Transactional}을 명시하여 Product 조회 및 저장이
 * 동일 트랜잭션 범위에서 처리되도록 합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 9.
 * @see REFGroceryItemCategoryChangedEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductCategoryUpdateByReassignmentHandler {

    private final REFProductRepository productRepository;
    private final REFProductLifeCycleService productLifeCycleService;

    @Async
    @EventListener
    @Transactional
    public void handle(REFGroceryItemCategoryChangedEvent event) {
        log.info("[Product 카테고리 갱신 핸들러] 이벤트 수신. " +
                        "groceryItemId={}, groceryItemName='{}', " +
                        "majorCategoryId={}, minorCategoryId={}",
                event.groceryItemId(), event.groceryItemName(),
                event.newMajorCategoryId(), event.newMinorCategoryId());

        String origProductName = event.origProductName();

        // origProductName 없으면 Product 처리 불필요
        if (origProductName == null || origProductName.isBlank()) {
            log.debug("[Product 카테고리 갱신 핸들러] origProductName 없음, 스킵. " +
                    "groceryItemId={}", event.groceryItemId());
            return;
        }

        boolean productExists = productRepository
                .existsByProductNameValueAndGroceryItemId(origProductName, event.groceryItemId());

        if (productExists) {
            // 기존 Product 카테고리 참조 갱신
            productRepository.findByProductNameAndGroceryItemId(origProductName, event.groceryItemId())
                    .ifPresent(product -> {
                        product.updateCategoryReference(
                                event.newMajorCategoryId(),
                                event.newMinorCategoryId()
                        );
                        productRepository.save(product);

                        log.info("[Product 카테고리 갱신 핸들러] 카테고리 참조 갱신 완료. " +
                                        "productName='{}', groceryItemId={}",
                                origProductName, event.groceryItemId());
                    });

        } else {
            // 신규 Product 등록
            productLifeCycleService.upsertProduct(
                    origProductName,
                    event.origBrandName(),
                    event.groceryItemId(),
                    event.newMajorCategoryId(),
                    event.newMinorCategoryId()
            );

            log.info("[Product 카테고리 갱신 핸들러] 신규 Product 등록 완료. " +
                            "productName='{}', groceryItemId={}",
                    origProductName, event.groceryItemId());
        }
    }
}