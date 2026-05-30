package com.refridge.core_server.groceryItem.infra.event;

import com.refridge.core_server.groceryItem.application.REFGroceryItemCategoricalService;
import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemCategoryChangeCommand;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.event.REFGroceryItemCategoryChangedEvent;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidateAndAdaptService;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryPathSeparator;
import com.refridge.core_server.recognition_feedback.domain.event.REFCategoryReassignmentApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Feedback BC가 발행한 카테고리 재분류 승인 이벤트를 GroceryItem BC에서 구독하는 핸들러입니다.
 *
 * <h3>BC 경계 준수</h3>
 * <p>
 * 이 핸들러는 GroceryItem BC가 자신의 영역에서 Feedback BC 이벤트를 구독하여
 * GroceryItem 카테고리 변경만 처리하고, Product 갱신은 이벤트로 위임합니다.
 * </p>
 *
 * <h3>처리 순서</h3>
 * <ol>
 *   <li>targetValue 파싱: {@code "{correctedGroceryItemName}::{correctedCategoryPath}"}</li>
 *   <li>categoryPath 파싱: {@code "{majorCategoryName} > {minorCategoryName}"}</li>
 *   <li>카테고리 ID 조회 (majorCategoryName → ID, minorCategoryName → ID)</li>
 *   <li>GroceryItem 이름으로 조회</li>
 *   <li><b>★ 멱등성 가드 — 이미 동일 카테고리면 early-return (F4)</b></li>
 *   <li>카테고리 변경 (실제 변경 발생)</li>
 *   <li>{@link REFGroceryItemCategoryChangedEvent} 발행 → Product BC로 전파 (커밋 후)</li>
 * </ol>
 *
 * <h3>트랜잭션 + 이벤트 발행 타이밍 (이슈 2.1 수정)</h3>
 * <p>
 * 기존에는 {@code @Transactional} 내에서 {@code eventPublisher.publishEvent()}를 호출했습니다.
 * 수신측 {@code REFProductCategoryUpdateByReassignmentHandler}도 {@code @Async}이므로
 * Spring이 새 스레드에서 실행하는데, 이 경우 GroceryItem 카테고리 변경 트랜잭션이
 * 커밋되기 전에 Product 갱신 핸들러가 DB를 조회하여 변경 전 데이터를 읽을 수 있습니다.
 * </p>
 * <p>
 * 수정 후에는 {@link TransactionSynchronizationManager#registerSynchronization}을 통해
 * {@link TransactionSynchronization#afterCommit()} 콜백에서만 이벤트를 발행합니다.
 * GroceryItem 카테고리 변경이 DB에 완전히 커밋된 이후에만 Product BC로 이벤트가 전파되어
 * 데이터 정합성이 보장됩니다.
 * </p>
 *
 * <h3>멱등성 — Step 7 Outbox 재전달 대비 (F4)</h3>
 * <p>
 * Outbox 인프라가 도입되면 같은 {@link REFCategoryReassignmentApprovedEvent}가
 * at-least-once로 두 번 이상 들어올 수 있습니다.
 * 이 경우 {@code categoricalService.changeCategory}가 조건부 UPDATE라면 DB 상태는 멱등하지만,
 * 핸들러가 매번 다음 단계 이벤트({@link REFGroceryItemCategoryChangedEvent})를 발행하면
 * Product BC가 중복 처리를 수행하게 됩니다.
 * </p>
 * <p>
 * 따라서 핸들러 레벨에서 조회된 GroceryItem의 현재 카테고리와 목표 카테고리를 비교하여,
 * 이미 동일하면 카테고리 변경 호출과 다음 이벤트 발행 모두를 스킵합니다.
 * 이로써 Outbox 재전달 시나리오에서 Product BC가 받는 이벤트도 정확히 1회로 보장됩니다.
 * </p>
 *
 * <h3>전제 조건</h3>
 * <p>
 * {@code REFGroceryItemRepository#findByGroceryItemName}이 반환하는 DTO는
 * {@code groceryItemId()}, {@code majorCategoryId()}, {@code minorCategoryId()} 메서드를 노출해야 합니다.
 * DTO가 카테고리 ID를 노출하지 않는다면 별도 조회를 추가해야 합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 9. (Step 7 F4 멱등성 강화: 2026. 5. 30.)
 * @see REFCategoryReassignmentApprovedEvent
 * @see REFGroceryItemCategoryChangedEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemCategoryReassignmentEventHandler {

    private final REFGroceryItemRepository groceryItemRepository;
    private final REFGroceryItemCategoricalService categoricalService;
    private final REFGroceryItemCategoryValidateAndAdaptService categoryValidateService;
    private final ApplicationEventPublisher eventPublisher;

    private static final String TARGET_SEPARATOR = "::";

    @Async
    @EventListener
    @Transactional
    public void handle(REFCategoryReassignmentApprovedEvent event) {
        log.info("[GroceryItem 카테고리 재분류 핸들러] 이벤트 수신. reviewId={}, targetValue='{}'",
                event.reviewId(), event.targetValue());

        // Step 1: targetValue 파싱
        String[] parts = event.targetValue().split(TARGET_SEPARATOR, 2);
        if (parts.length < 2) {
            log.error("[GroceryItem 카테고리 재분류 핸들러] targetValue 형식 오류. " +
                    "reviewId={}, targetValue='{}'", event.reviewId(), event.targetValue());
            return;
        }

        String correctedGroceryItemName = parts[0];
        String correctedCategoryPath = parts[1];

        // Step 2: categoryPath 파싱
        String[] categoryParts = correctedCategoryPath
                .split(REFGroceryCategoryPathSeparator.SEPARATOR_REGEX, 2);
        if (categoryParts.length < 2) {
            log.error("[GroceryItem 카테고리 재분류 핸들러] categoryPath 형식 오류. path='{}'",
                    correctedCategoryPath);
            return;
        }

        String majorCategoryName = categoryParts[0].trim();
        String minorCategoryName = categoryParts[1].trim();

        // Step 3: 카테고리 ID 조회
        final Long newMajorCategoryId;
        final Long newMinorCategoryId;
        try {
            newMajorCategoryId = categoryValidateService.findMajorCategoryIdByName(majorCategoryName);
            newMinorCategoryId = categoryValidateService.findMinorCategoryIdByName(minorCategoryName);
        } catch (IllegalArgumentException e) {
            log.error("[GroceryItem 카테고리 재분류 핸들러] 카테고리 ID 조회 실패. " +
                            "major='{}', minor='{}', 사유: {}",
                    majorCategoryName, minorCategoryName, e.getMessage());
            return;
        }

        // Step 4: GroceryItem 조회 및 카테고리 변경
        groceryItemRepository.findByGroceryItemName(correctedGroceryItemName)
                .ifPresentOrElse(
                        groceryItemDto -> {
                            Long groceryItemId = groceryItemDto.groceryItemId();

                            // ── F4 멱등성 가드 ─────────────────────────────────────
                            // Outbox 재전달(at-least-once)로 같은 이벤트가 재유입되더라도,
                            // 이미 목표 카테고리로 변경 완료된 상태라면:
                            //   1) categoricalService.changeCategory 호출 스킵
                            //   2) 다음 단계 REFGroceryItemCategoryChangedEvent 발행 스킵
                            // 이로써 Product BC가 받는 cascade도 정확히 1회로 보장된다.
                            if (newMajorCategoryId.equals(groceryItemDto.majorCategoryId())
                                    && newMinorCategoryId.equals(groceryItemDto.minorCategoryId())) {
                                log.debug("[GroceryItem 카테고리 재분류 핸들러] 이미 동일 카테고리 — 멱등 스킵. " +
                                                "groceryItemId={}, reviewId={}",
                                        groceryItemId, event.reviewId());
                                return;
                            }

                            categoricalService.changeCategory(
                                    REFGroceryItemCategoryChangeCommand.builder()
                                            .groceryItemId(groceryItemId)
                                            .majorCategoryId(newMajorCategoryId)
                                            .minorCategoryId(newMinorCategoryId)
                                            .build()
                            );

                            log.info("[GroceryItem 카테고리 재분류 핸들러] 카테고리 변경 완료. " +
                                            "groceryItemId={}, '{}' → '{}'",
                                    groceryItemId, correctedGroceryItemName, correctedCategoryPath);

                            // Step 5: Product BC로 이벤트 전파 — 커밋 후 발행
                            // @Async 수신측이 커밋 전 DB를 조회하는 타이밍 문제를 방지합니다.
                            TransactionSynchronizationManager.registerSynchronization(
                                    new TransactionSynchronization() {
                                        @Override
                                        public void afterCommit() {
                                            eventPublisher.publishEvent(
                                                    new REFGroceryItemCategoryChangedEvent(
                                                            groceryItemId,
                                                            correctedGroceryItemName,
                                                            newMajorCategoryId,
                                                            newMinorCategoryId,
                                                            event.origProductName(),
                                                            event.origBrandName(),
                                                            event.sourceFeedbackId()
                                                    )
                                            );
                                            log.info("[GroceryItem 카테고리 재분류 핸들러] " +
                                                            "커밋 후 Product BC 이벤트 발행. groceryItemId={}",
                                                    groceryItemId);
                                        }
                                    }
                            );
                        },
                        () -> log.warn(
                                "[GroceryItem 카테고리 재분류 핸들러] GroceryItem 없음. " +
                                        "name='{}', reviewId={}",
                                correctedGroceryItemName, event.reviewId())
                );
    }
}