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
 *   <li>GroceryItem 이름으로 조회 후 카테고리 변경</li>
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
 * @author 이승훈
 * @since 2026. 4. 9.
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
        Long newMajorCategoryId;
        Long newMinorCategoryId;
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
                            final Long finalMajorCategoryId = newMajorCategoryId;
                            final Long finalMinorCategoryId = newMinorCategoryId;

                            TransactionSynchronizationManager.registerSynchronization(
                                    new TransactionSynchronization() {
                                        @Override
                                        public void afterCommit() {
                                            eventPublisher.publishEvent(
                                                    new REFGroceryItemCategoryChangedEvent(
                                                            groceryItemId,
                                                            correctedGroceryItemName,
                                                            finalMajorCategoryId,
                                                            finalMinorCategoryId,
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