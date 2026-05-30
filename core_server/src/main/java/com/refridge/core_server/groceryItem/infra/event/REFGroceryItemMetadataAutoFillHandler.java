package com.refridge.core_server.groceryItem.infra.event;

import com.refridge.core_server.groceryItem.application.REFGroceryItemMetadataService;
import com.refridge.core_server.groceryItem.domain.event.REFGroceryItemCreatedEvent;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemBootstrapDTO;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * 신규 GroceryItem 생성 시 카테고리 디폴트 프로필 + 특수 케이스 오버라이드를
 * 자동으로 적용하는 비동기 이벤트 핸들러입니다.
 *
 * <h3>실행 트리거</h3>
 * <p>
 * {@link REFGroceryItemCreatedEvent} 발행 시. 발행 경로:
 * </p>
 * <pre>
 *   LifeCycleService.createGroceryItem (단건)
 *   LifeCycleService.createGroceryItemBulk (다건)
 *   LifeCycleService.upsert (인식 파이프라인 신규 생성)
 *     ↓
 *   REFGroceryItem.create() → @PostPersist에서 이벤트 등록
 *     ↓
 *   repository.save() / saveAll() → AbstractAggregateRoot가 자동 publish
 *     ↓
 *   이 핸들러 실행 (AFTER_COMMIT, 비동기, 전용 Executor)
 * </pre>
 *
 * <h3>왜 @TransactionalEventListener(AFTER_COMMIT)인가</h3>
 * <p>
 * GroceryItem 저장 트랜잭션이 완전히 커밋된 후에 메타데이터를 채워야 합니다.
 * 만약 BEFORE_COMMIT이나 일반 @EventListener를 쓰면 핸들러가 동일 트랜잭션에 참여하여
 * GroceryItem이 아직 DB에 없는 상태에서 조회하려 시도할 수 있습니다.
 * </p>
 *
 * <h3>왜 별도 Executor "metadataAutoFillExecutor"인가</h3>
 * <p>
 * Bulk 생성(1000건) 시에도 핸들러가 동시 N개 이상 실행되지 않도록 동시성을 제한합니다.
 * {@link com.refridge.core_server.config.REFGroceryItemAsyncConfig}에서
 * corePoolSize=4로 정의되어 DB 커넥션 풀 부담을 제어합니다.
 * </p>
 *
 * <h3>실패 처리</h3>
 * <p>
 * 메타데이터 적용 실패는 GroceryItem 생성 자체를 롤백하지 않습니다 (이미 커밋됨).
 * 실패 시 WARN 로그만 남기고, 부트스트랩 Runner가 다음 부팅 시 재시도 가능합니다
 * (overrideExisting=false라면 이미 채워진 것은 보존되고 빈 것만 다시 시도됨).
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 14.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFGroceryItemMetadataAutoFillHandler {

    private final REFGroceryItemMetadataService metadataService;
    private final REFMajorGroceryCategoryRepository majorCategoryRepository;
    private final REFMinorGroceryCategoryRepository minorCategoryRepository;

    /**
     * 신규 GroceryItem 생성 후 카테고리 디폴트 프로필 + 특수 케이스 적용.
     */
    @Async("metadataAutoFillExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(REFGroceryItemCreatedEvent event) {
        log.info("[메타데이터 자동 채움] 이벤트 수신. id={}, name='{}', major/minor={}/{}",
                event.groceryItemId(), event.groceryItemName(),
                event.majorCategoryId(), event.minorCategoryId());

        try {
            REFGroceryItemBootstrapDTO bootstrapDto = resolveCategoryNames(event);
            if (bootstrapDto == null) {
                log.warn("[메타데이터 자동 채움] 카테고리 이름 조회 실패 — 스킵. id={}, " +
                                "major/minor={}/{}", event.groceryItemId(),
                        event.majorCategoryId(), event.minorCategoryId());
                return;
            }

            // 신규 생성이므로 overrideExisting=true — 빈 메타데이터를 프로필 값으로 채움
            boolean applied = metadataService.applyDefaultProfileFromBootstrap(bootstrapDto, true);

            if (applied) {
                log.info("[메타데이터 자동 채움] 적용 완료. id={}, name='{}'",
                        event.groceryItemId(), event.groceryItemName());
            } else {
                log.warn("[메타데이터 자동 채움] 매핑 프로필 없음 — 빈 메타데이터 유지. " +
                                "id={}, minor='{}', major='{}'",
                        event.groceryItemId(),
                        bootstrapDto.minorCategoryName(), bootstrapDto.majorCategoryName());
            }
        } catch (Exception e) {
            // 실패해도 GroceryItem 자체는 이미 커밋됨 — 다음 부트스트랩에서 재시도
            log.warn("[메타데이터 자동 채움] 적용 실패. id={}, name='{}', 사유: {}",
                    event.groceryItemId(), event.groceryItemName(), e.getMessage());
        }
    }

    /**
     * 이벤트의 categoryId로부터 categoryName을 조회하여 부트스트랩 DTO를 구성.
     * 카테고리가 삭제되었거나 정상 상태가 아닐 때 null 반환.
     */
    @Transactional(readOnly = true)
    public REFGroceryItemBootstrapDTO resolveCategoryNames(REFGroceryItemCreatedEvent event) {
        // Repository에 추가된 projection 메서드 활용 (findCategoryNameById)
        Optional<String> minorName = minorCategoryRepository.findCategoryNameById(event.minorCategoryId());
        Optional<String> majorName = majorCategoryRepository.findCategoryNameById(event.majorCategoryId());

        if (minorName.isEmpty() || majorName.isEmpty()) {
            return null;
        }

        return new REFGroceryItemBootstrapDTO(
                event.groceryItemId(),
                event.groceryItemName(),
                minorName.get(),
                majorName.get()
        );
    }
}