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
 * <h3>BC 경계 준수</h3>
 * <p>
 * Feedback BC는 GroceryItem BC를 직접 참조하지 않고 이벤트를 발행합니다.
 * GroceryItem BC가 자신의 영역에서 이벤트를 구독하여 자체적으로 처리한 뒤,
 * 필요한 경우 Product BC로 새로운 이벤트를 전파합니다.
 * </p>
 *
 * <h3>처리 분기</h3>
 * <pre>
 *   correctedName → GroceryItem DB 조회
 *     ├── 존재 O → 카테고리 ID 포함 상세 DTO 추가 조회
 *     │             → REFGroceryItemCorrectionAppliedEvent 발행
 *     └── 존재 X → 스킵
 *                   (NEW_GROCERY_ITEM 검수 큐는 REFGroceryItemMappingHandler가 이미 적재)
 * </pre>
 *
 * <h3>쿼리 구조</h3>
 * <p>
 * {@code findByGroceryItemName()}은 카테고리 경로 텍스트를 반환하지만
 * 카테고리 ID를 포함하지 않습니다.
 * {@code findDetailDTOById()}는 카테고리 ID(majorCategoryId, minorCategoryId)를
 * 포함하는 경량 Projection 쿼리입니다.
 * AR 전체를 로딩하는 {@code findById()} 대신 사용하여 불필요한 컬럼 로딩을 방지합니다.
 * </p>
 *
 * <h3>트랜잭션</h3>
 * <p>
 * {@code @Async} 메서드는 호출자의 트랜잭션을 상속받지 않습니다.
 * {@code @Transactional(readOnly = true)}를 명시하여 두 쿼리가
 * 동일 트랜잭션 내에서 일관된 스냅샷으로 실행되도록 합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 * @see REFGroceryItemCorrectionConfirmedEvent
 * @see REFGroceryItemCorrectionAppliedEvent
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
        log.info("[GroceryItem 교정 핸들러] 이벤트 수신. '{}' → '{}'",
                event.originalName(), event.correctedName());

        // Step 1: correctedName으로 groceryItemId 획득
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
     * <p>
     * AR 전체 로딩({@code findById()}) 대신 Projection({@code findDetailDTOById()})을
     * 사용하여 필요한 컬럼만 조회합니다.
     * </p>
     */
    private void fetchDetailAndPublish(REFGroceryItemCorrectionConfirmedEvent event,
                                       Long groceryItemId) {
        // Step 2: 카테고리 ID 포함 상세 DTO 조회
        groceryItemRepository.findDetailDTOById(groceryItemId)
                .ifPresentOrElse(
                        detailDto -> {
                            eventPublisher.publishEvent(new REFGroceryItemCorrectionAppliedEvent(
                                    event.originalName(),
                                    event.correctedName(),
                                    groceryItemId,
                                    detailDto.majorCategoryId(),
                                    detailDto.minorCategoryId()
                            ));

                            log.info("[GroceryItem 교정 핸들러] Product BC로 이벤트 전파. " +
                                            "originalName='{}', correctedName='{}', " +
                                            "groceryItemId={}, majorCategoryId={}, minorCategoryId={}",
                                    event.originalName(), event.correctedName(),
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