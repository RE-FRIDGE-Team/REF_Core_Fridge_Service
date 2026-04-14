package com.refridge.core_server.groceryItem.infra.event;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.event.REFGroceryItemCorrectionAppliedEvent;
import com.refridge.core_server.recognition_feedback.domain.event.REFGroceryItemCorrectionConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feedback BC가 발행한 식재료명 교정 확정 이벤트를 GroceryItem BC에서 구독하는 핸들러입니다.
 *
 * <h3>변경 사항 (2026. 4. 14.)</h3>
 * <p>
 * {@code fetchDetailAndPublish()}에서 {@link REFGroceryItemCorrectionAppliedEvent}를 발행할 때
 * {@code originalProductName}이 있으면 그것을, 없으면 {@code originalName}(식재료명)을
 * 폴백으로 사용합니다.
 * </p>
 * <p>
 * Product BC는 이 값을 {@code productName}으로 Product를 upsert합니다.
 * 실제 제품명이 productName으로 등록되어야 다음 인식 시
 * {@code ProductIndexSearch}가 올바르게 매칭할 수 있습니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 8. (수정: 2026. 4. 14.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemCorrectionEventHandler {

    private final REFGroceryItemRepository groceryItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    @Transactional(readOnly = true)
    public void handle(REFGroceryItemCorrectionConfirmedEvent event) {
        log.info("[GroceryItem 교정 핸들러] 이벤트 수신. '{}' → '{}', 제품명='{}'",
                event.originalName(), event.correctedName(), event.originalProductName());

        groceryItemRepository.findByGroceryItemName(event.correctedName())
                .ifPresentOrElse(
                        groceryItemDto -> fetchDetailAndPublish(event, groceryItemDto.groceryItemId()),
                        () -> log.info(
                                "[GroceryItem 교정 핸들러] correctedName이 DB에 없음, 스킵. " +
                                        "correctedName='{}' (검수 큐에 이미 적재됨)",
                                event.correctedName())
                );
    }

    /**
     * groceryItemId로 카테고리 ID 포함 상세 DTO를 조회하여 이벤트를 발행합니다.
     *
     * <h3>productName 결정 로직</h3>
     * <ul>
     *   <li>{@code originalProductName} 존재: 실제 제품명 사용 → ProductIndexSearch 매칭 가능</li>
     *   <li>{@code originalProductName} null: {@code originalName}(식재료명) 폴백 사용</li>
     * </ul>
     */
    private void fetchDetailAndPublish(REFGroceryItemCorrectionConfirmedEvent event,
                                       Long groceryItemId) {
        groceryItemRepository.findDetailDTOById(groceryItemId)
                .ifPresentOrElse(
                        detailDto -> {
                            // originalProductName이 있으면 실제 제품명 사용,
                            // 없으면 originalName(식재료명) 폴백
                            String productNameForUpsert =
                                    (event.originalProductName() != null
                                            && !event.originalProductName().isBlank())
                                            ? event.originalProductName()
                                            : event.originalName();

                            eventPublisher.publishEvent(new REFGroceryItemCorrectionAppliedEvent(
                                    productNameForUpsert,       // Product upsert 시 productName
                                    event.correctedName(),
                                    groceryItemId,
                                    detailDto.majorCategoryId(),
                                    detailDto.minorCategoryId()
                            ));

                            log.info("[GroceryItem 교정 핸들러] Product BC로 이벤트 전파. " +
                                            "productName='{}', correctedName='{}', " +
                                            "groceryItemId={}, majorCategoryId={}, minorCategoryId={}",
                                    productNameForUpsert, event.correctedName(),
                                    groceryItemId,
                                    detailDto.majorCategoryId(),
                                    detailDto.minorCategoryId());
                        },
                        () -> log.warn(
                                "[GroceryItem 교정 핸들러] 상세 DTO 조회 실패. groceryItemId={}",
                                groceryItemId)
                );
    }
}