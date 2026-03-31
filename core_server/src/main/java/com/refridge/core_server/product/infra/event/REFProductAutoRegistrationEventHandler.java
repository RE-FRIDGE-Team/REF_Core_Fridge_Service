package com.refridge.core_server.product.infra.event;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.product.application.REFProductLifeCycleService;
import com.refridge.core_server.recognition_feedback.domain.event.REFProductAutoRegistrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Product 자동 등록 이벤트를 구독하여 Product를 upsert하는 핸들러입니다.
 * <p>
 * Product BC에 위치하며, 피드백 BC가 발행한 {@link REFProductAutoRegistrationEvent}를 소비합니다.
 *
 * <h3>카테고리 ID 조회 책임</h3>
 * 이벤트에는 groceryItemId만 포함됩니다. majorCategoryId/minorCategoryId는
 * 이 핸들러가 REFGroceryItemRepository를 통해 직접 조회합니다.
 * 피드백 BC가 GroceryItem 구조를 알지 않아도 되도록 책임을 여기서 부담합니다.
 *
 * <h3>멱등성</h3>
 * {@code upsertProduct()}는 동일한 productName + groceryItemId 조합이 이미 존재하면
 * INSERT를 건너뜁니다. 따라서 이벤트가 중복 발행되어도 안전합니다.
 *
 * <h3>비동기 처리</h3>
 * {@code @Async}로 별도 스레드에서 실행됩니다.
 * 피드백 집계 흐름의 응답 시간에 영향을 주지 않습니다.
 * 비동기 실패 시 이벤트가 재시도되지 않으므로, 필요하면 Outbox 패턴 도입을 고려하세요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductAutoRegistrationEventHandler {

    private final REFProductLifeCycleService productLifeCycleService;
    private final REFGroceryItemRepository groceryItemRepository;

    @Async
    @EventListener
    public void handle(REFProductAutoRegistrationEvent event) {
        Long groceryItemId = event.groceryItemId();

        if (groceryItemId == null) {
            log.warn("[Product 자동등록] groceryItemId 누락으로 등록 생략. productName='{}'",
                    event.productName());
            return;
        }

        // groceryItemId로 majorCategoryId/minorCategoryId 조회
        // Product BC 핸들러가 직접 조회 — 피드백 BC가 카테고리 구조를 알 필요 없음
        REFGroceryItem groceryItem = groceryItemRepository.findById(groceryItemId)
                .orElse(null);

        if (groceryItem == null) {
            log.warn("[Product 자동등록] groceryItemId={}에 해당하는 GroceryItem 없음. productName='{}'",
                    groceryItemId, event.productName());
            return;
        }

        Long majorCategoryId = groceryItem.getMajorCategoryId();
        Long minorCategoryId = groceryItem.getMinorCategoryId();

        try {
            productLifeCycleService.upsertProduct(
                    event.productName(),
                    event.brandName(),
                    groceryItemId,
                    majorCategoryId,
                    minorCategoryId
            );

            log.info("[Product 자동등록] 완료. productName='{}', groceryItemId={}, major={}, minor={}",
                    event.productName(), groceryItemId, majorCategoryId, minorCategoryId);

        } catch (Exception e) {
            log.error("[Product 자동등록] 실패. productName='{}', groceryItemId={}, 사유: {}",
                    event.productName(), groceryItemId, e.getMessage());
        }
    }
}