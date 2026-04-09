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
 *   <li>{@link REFGroceryItemCategoryChangedEvent} 발행 → Product BC로 전파</li>
 * </ol>
 *
 * <h3>트랜잭션</h3>
 * <p>
 * {@code @Async} 메서드는 호출자의 트랜잭션을 상속받지 않습니다.
 * {@code @Transactional}을 명시하여 카테고리 변경과 이벤트 발행이
 * 동일 트랜잭션 범위에서 처리되도록 합니다.
 * 카테고리 변경 실패 시 이벤트가 발행되지 않아 Product BC 갱신도 일어나지 않습니다.
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
        // 형식: "{correctedGroceryItemName}::{correctedCategoryPath}"
        String[] parts = event.targetValue().split(TARGET_SEPARATOR, 2);
        if (parts.length < 2) {
            log.error("[GroceryItem 카테고리 재분류 핸들러] targetValue 형식 오류. " +
                    "reviewId={}, targetValue='{}'", event.reviewId(), event.targetValue());
            return;
        }

        String correctedGroceryItemName = parts[0];
        String correctedCategoryPath = parts[1];

        // Step 2: categoryPath 파싱
        // 형식: "{majorCategoryName} > {minorCategoryName}"
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

                            // Step 5: Product BC로 이벤트 전파
                            eventPublisher.publishEvent(new REFGroceryItemCategoryChangedEvent(
                                    groceryItemId,
                                    correctedGroceryItemName,
                                    newMajorCategoryId,
                                    newMinorCategoryId,
                                    event.origProductName(),
                                    event.origBrandName(),
                                    event.sourceFeedbackId()
                            ));
                        },
                        () -> log.warn(
                                "[GroceryItem 카테고리 재분류 핸들러] GroceryItem 없음. " +
                                        "name='{}', reviewId={}",
                                correctedGroceryItemName, event.reviewId())
                );
    }
}