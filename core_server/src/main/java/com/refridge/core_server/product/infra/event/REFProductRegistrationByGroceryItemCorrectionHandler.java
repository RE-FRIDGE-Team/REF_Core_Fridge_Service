package com.refridge.core_server.product.infra.event;

import com.refridge.core_server.groceryItem.domain.event.REFGroceryItemCorrectionAppliedEvent;
import com.refridge.core_server.product.application.REFProductLifeCycleService;
import com.refridge.core_server.product.domain.event.REFProductRegisteredByGroceryItemCorrectionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GroceryItem BC가 발행한 식재료명 교정 적용 이벤트를 Product BC에서 구독하는 핸들러입니다.
 *
 * <h3>BC 경계 준수</h3>
 * <p>
 * GroceryItem BC는 Product BC를 직접 참조하지 않고 이벤트를 발행합니다.
 * Product BC가 자신의 영역에서 이벤트를 구독하여 Product upsert를 처리합니다.
 * 완료 후 Feedback BC로 {@link REFProductRegisteredByGroceryItemCorrectionEvent}를 전파합니다.
 * </p>
 *
 * <h3>Product upsert를 하는 이유</h3>
 * <p>
 * 파이프라인 순서는 {@code ProductIndexSearch → GroceryItemDict → ML}입니다.
 * 교정이 확정된 시점에 originalName을 제품명으로 하는 Product를 미리 등록해두면,
 * 다음 동일 제품 인식 시 ProductIndexSearch 단계에서 올바른 식재료로 우선 매칭되어
 * GroceryItemDict/ML까지 내려가는 오분류를 예방합니다.
 * </p>
 *
 * <h3>brandName을 null로 전달하는 이유</h3>
 * <p>
 * 식재료명 교정 흐름에서는 브랜드 정보를 알 수 없습니다.
 * ProductIndexSearch는 제품명(productName) 기준으로 매칭하므로
 * brandName 없이도 올바르게 동작합니다.
 * </p>
 *
 * <h3>트랜잭션</h3>
 * <p>
 * {@code @Async} 메서드는 호출자의 트랜잭션을 상속받지 않습니다.
 * {@code upsertProduct()}가 내부적으로 {@code @Transactional}을 보유하고 있더라도
 * 이 핸들러에 {@code @Transactional}을 명시하여 upsert 작업과 이벤트 발행이
 * 동일 트랜잭션 범위 내에서 처리되도록 합니다.
 * upsert 실패 시 이벤트가 발행되지 않아 Feedback BC의 플래그 세팅도 일어나지 않습니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 * @see REFGroceryItemCorrectionAppliedEvent
 * @see REFProductRegisteredByGroceryItemCorrectionEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductRegistrationByGroceryItemCorrectionHandler {

    private final REFProductLifeCycleService productLifeCycleService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    @Transactional
    public void handle(REFGroceryItemCorrectionAppliedEvent event) {
        log.info("[Product 교정 등록 핸들러] 이벤트 수신. " +
                        "originalName='{}', correctedName='{}', groceryItemId={}",
                event.originalName(), event.correctedName(), event.groceryItemId());

        // originalName을 제품명으로 Product upsert
        // brandName은 null — 제품명 기준 ProductIndexSearch 매칭으로 충분
        productLifeCycleService.upsertProduct(
                event.originalName(),
                null,
                event.groceryItemId(),
                event.majorCategoryId(),
                event.minorCategoryId()
        );

        log.info("[Product 교정 등록 핸들러] Product upsert 완료. " +
                        "originalName='{}', groceryItemId={}",
                event.originalName(), event.groceryItemId());

        // Feedback BC로 완료 이벤트 전파
        // Feedback BC가 자신의 Redis 키(feedback:registered)를 직접 관리하도록
        // originalName만 전달하고 세팅 책임은 Feedback BC에 위임
        eventPublisher.publishEvent(new REFProductRegisteredByGroceryItemCorrectionEvent(
                event.originalName(),
                event.correctedName(),
                event.groceryItemId()
        ));
    }
}