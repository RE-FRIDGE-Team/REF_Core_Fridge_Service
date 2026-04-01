package com.refridge.core_server.product.infra.event;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.product.application.REFProductLifeCycleService;
import com.refridge.core_server.recognition_feedback.domain.event.REFProductFeedbackAggregationEvent;
import com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Product 피드백 집계 이벤트를 구독하여 Product를 등록하는 핸들러입니다.
 * Product BC에 위치하며, 피드백 BC가 발행한 이벤트를 소비합니다.
 *
 * <h3>등록 값</h3>
 * 파이프라인이 인식한 값을 그대로 사용합니다.
 * 긍정 피드백 우세 = 인식 결과가 맞다는 사용자 확인이므로
 * 별도 보정 없이 snapshot의 값을 그대로 Product에 저장합니다.
 *
 * <h3>등록 완료 후 Redis REGISTERED 플래그 세팅</h3>
 * upsertProduct() 성공 후 Redis에 플래그를 세팅합니다.
 * 이후 피드백 BC의 핸들러는 이 플래그를 보고 O(1)으로 스킵합니다.
 *
 * <h3>멱등성</h3>
 * upsertProduct()는 동일 productName + groceryItemId 조합이 이미 존재하면
 * INSERT를 건너뜁니다. 중복 이벤트에도 안전합니다.
 *
 * <h3>비동기 처리</h3>
 * {@code @Async}로 별도 스레드에서 실행됩니다.
 * 피드백 집계 흐름의 응답 시간에 영향을 주지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductFeedbackAggregationEventHandler {

    private final REFProductLifeCycleService productLifeCycleService;
    private final REFGroceryItemRepository groceryItemRepository;
    private final StringRedisTemplate redisTemplate;

    static final String REGISTERED_FLAG_PREFIX  = "feedback:registered:";

    @Async
    @EventListener
    public void handle(REFProductFeedbackAggregationEvent event) {
        Long groceryItemId = event.groceryItemId();
        String productName = event.productName();

        if (groceryItemId == null) {
            log.warn("[Product 자동등록] groceryItemId 누락, 등록 생략. productName='{}'", productName);
            return;
        }

        // ── groceryItemId로 카테고리 ID 조회 ──────────────────
        // majorCategoryId/minorCategoryId는 피드백 BC가 아닌 여기서 조회
        REFGroceryItem groceryItem = groceryItemRepository.findById(groceryItemId)
                .orElse(null);

        if (groceryItem == null) {
            log.warn("[Product 자동등록] groceryItemId={}에 해당하는 GroceryItem 없음. productName='{}'",
                    groceryItemId, productName);
            return;
        }

        try {
            // 파이프라인 인식값 그대로 등록 — 별도 보정 없음
            productLifeCycleService.upsertProduct(
                    productName,
                    event.brandName(),
                    groceryItemId,
                    groceryItem.getMajorCategoryId(),
                    groceryItem.getMinorCategoryId()
            );

            // ── 등록 완료 후 Redis REGISTERED 플래그 세팅 ─────
            // 이 플래그가 있으면 피드백 BC 핸들러가 O(1)으로 스킵
            String flagKey = REGISTERED_FLAG_PREFIX + productName;
            redisTemplate.opsForValue().set(flagKey, "1");

            log.info("[Product 자동등록] 완료. productName='{}', brandName='{}', groceryItemId={}",
                    productName, event.brandName(), groceryItemId);

        } catch (Exception e) {
            // 등록 실패 시 REGISTERED 플래그를 세팅하지 않습니다.
            // 다음 긍정 피드백 이벤트에서 자동으로 재시도됩니다.
            log.error("[Product 자동등록] 실패. productName='{}', groceryItemId={}, 사유: {}",
                    productName, groceryItemId, e.getMessage());
        }
    }
}