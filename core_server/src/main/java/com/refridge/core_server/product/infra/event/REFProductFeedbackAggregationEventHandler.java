package com.refridge.core_server.product.infra.event;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.product.application.REFProductLifeCycleService;
import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
import com.refridge.core_server.recognition_feedback.domain.event.REFProductFeedbackAggregationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Product 피드백 집계 이벤트를 구독하여 Product를 등록하는 핸들러입니다.
 *
 * <h3>등록 제품명 결정 우선순위</h3>
 * <pre>
 *   1순위: alias:confirmed에 CONFIRMED alias가 있으면 alias명으로 등록
 *   2순위: alias가 없으면 파이프라인 원본 정제명으로 등록
 * </pre>
 *
 * alias명으로 등록하는 이유:
 *   alias = 사용자들이 검증한 올바른 제품명.
 *   예) "해찬들 초고추장 340ml" → alias "해찬들 초고추장"
 *   Product DB에 "해찬들 초고추장"으로 등록되면
 *   이후 ProductIndexSearch가 정확하게 매칭할 수 있습니다.
 *
 * <h3>피드백 집계 기준 유지</h3>
 * 이 핸들러는 Product 등록 시에만 alias를 적용합니다.
 * AR 저장, 피드백 집계(orig_product_name)는 원본명 기준을 유지합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductFeedbackAggregationEventHandler {

    private final REFProductLifeCycleService productLifeCycleService;
    private final REFGroceryItemRepository groceryItemRepository;
    private final REFAliasConfirmationService aliasConfirmationService;
    private final StringRedisTemplate redisTemplate;

    static final String REGISTERED_FLAG_PREFIX  = "feedback:registered:";


    @Async
    @EventListener
    public void handle(REFProductFeedbackAggregationEvent event) {
        Long groceryItemId = event.groceryItemId();
        String originalProductName = event.productName();

        if (groceryItemId == null) {
            log.warn("[Product 자동등록] groceryItemId 누락. productName='{}'", originalProductName);
            return;
        }

        REFGroceryItem groceryItem = groceryItemRepository.findById(groceryItemId)
                .orElse(null);

        if (groceryItem == null) {
            log.warn("[Product 자동등록] GroceryItem 없음. groceryItemId={}, productName='{}'",
                    groceryItemId, originalProductName);
            return;
        }

        // ── alias 조회 → 등록 제품명 결정 ────────────────────────
        // alias가 있으면 alias명, 없으면 원본 파싱 결과명
        String finalProductName = aliasConfirmationService
                .findConfirmedAlias(originalProductName)
                .orElse(originalProductName);

        if (!finalProductName.equals(originalProductName)) {
            log.info("[Product 자동등록] alias 적용. '{}' → '{}'",
                    originalProductName, finalProductName);
        }

        try {
            productLifeCycleService.upsertProduct(
                    finalProductName,
                    event.brandName(),
                    groceryItemId,
                    groceryItem.getMajorCategoryId(),
                    groceryItem.getMinorCategoryId()
            );

            // ── REGISTERED 플래그 세팅 ────────────────────────────
            // 원본명 기준으로 세팅 — 긍정 피드백 카운터도 원본명 기준이므로 일관성 유지
            String flagKey = REGISTERED_FLAG_PREFIX + originalProductName;
            redisTemplate.opsForValue().set(flagKey, "1");

            log.info("[Product 자동등록] 완료. originalName='{}', registeredName='{}', groceryItemId={}",
                    originalProductName, finalProductName, groceryItemId);

        } catch (Exception e) {
            // 실패 시 REGISTERED 플래그 미세팅 → 다음 긍정 피드백에서 재시도
            log.error("[Product 자동등록] 실패. productName='{}', groceryItemId={}, 사유: {}",
                    originalProductName, groceryItemId, e.getMessage());
        }
    }
}