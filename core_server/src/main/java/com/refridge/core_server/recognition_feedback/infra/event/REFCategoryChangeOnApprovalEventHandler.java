package com.refridge.core_server.recognition_feedback.infra.event;

import com.refridge.core_server.groceryItem.application.REFGroceryItemCategoricalService;
import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemCategoryChangeCommand;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidateAndAdaptService;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryPathSeparator;
import com.refridge.core_server.product.application.REFProductLifeCycleService;
import com.refridge.core_server.product.domain.REFProductRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFCategoryReassignmentApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 카테고리 재분류 승인 이벤트를 처리하는 핸들러입니다.
 *
 * <h3>처리 순서</h3>
 * <ol>
 *   <li>targetValue 파싱: "{correctedGroceryItemName}::{correctedCategoryPath}"</li>
 *   <li>categoryPath 파싱: "{majorName} &gt; {minorName}"</li>
 *   <li>새 카테고리 ID 조회 (majorCategoryName → ID, minorCategoryName → ID)</li>
 *   <li>GroceryItem 이름으로 조회 후 카테고리 변경</li>
 *   <li>Product 존재 여부 확인 (origProductName + groceryItemId 기준)</li>
 *   <li>Product 존재 → 비정규화된 카테고리 참조({@code REFGroceryItemReference}) 갱신</li>
 *   <li>Product 없음 → 수정된 GroceryItem 매핑으로 신규 Product upsert</li>
 * </ol>
 *
 * <h3>왜 Product도 업데이트하는가</h3>
 * <p>
 * {@code REFProduct}는 {@code REFGroceryItemReference}에
 * {@code majorCategoryId}, {@code minorCategoryId}를 비정규화합니다.
 * GroceryItem의 카테고리가 변경되어도 Product의 비정규화 필드는 자동으로 갱신되지 않으므로
 * 명시적으로 업데이트합니다.
 * </p>
 *
 * <h3>Product가 없는 경우 신규 생성 이유</h3>
 * <p>
 * 인식 결과(Recognition AR)는 존재하지만 아직 Product로 등록되지 않은 상태입니다.
 * 파이프라인 순서가 {@code Parsing → Exclusion → ProductIndexSearch → GroceryItemDict → ML}이므로,
 * 수정된 카테고리로 Product를 미리 등록해두면
 * 다음 동일 제품 인식 시 ProductIndexSearch 단계에서 우선 매칭되어
 * GroceryItemDict/ML까지 내려가는 오분류를 예방합니다.
 * </p>
 *
 * <h3>비동기 실행</h3>
 * <p>
 * {@code @Async}로 실행되어 관리자 승인 트랜잭션을 블로킹하지 않습니다.
 * GroceryItem 카테고리 변경과 Product 처리는 각각 별도 트랜잭션으로 수행됩니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFCategoryReassignmentApprovedEvent
 * @see com.refridge.core_server.recognition_feedback.application.REFReviewAdminService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFCategoryChangeOnApprovalEventHandler {

    private final REFGroceryItemRepository groceryItemRepository;
    private final REFGroceryItemCategoricalService categoricalService;
    private final REFGroceryItemCategoryValidateAndAdaptService categoryValidateService;
    private final REFProductRepository productRepository;
    private final REFProductLifeCycleService productLifeCycleService;

    private static final String TARGET_SEPARATOR = "::";

    /**
     * 카테고리 재분류 승인 이벤트를 수신하여 GroceryItem 및 Product의 카테고리를 갱신합니다.
     *
     * @param event 카테고리 재분류 승인 이벤트
     */
    @Async
    @EventListener
    public void handle(REFCategoryReassignmentApprovedEvent event) {
        log.info("[카테고리 변경] 이벤트 수신. reviewId={}, targetValue='{}'",
                event.reviewId(), event.targetValue());

        // ── Step 1: targetValue 파싱 ──────────────────────────────
        // 형식: "{correctedGroceryItemName}::{correctedCategoryPath}"
        String[] parts = event.targetValue().split(TARGET_SEPARATOR, 2);
        if (parts.length < 2) {
            log.error("[카테고리 변경] targetValue 형식 오류. reviewId={}, targetValue='{}'",
                    event.reviewId(), event.targetValue());
            return;
        }

        String correctedGroceryItemName = parts[0];
        String correctedCategoryPath = parts[1];

        // ── Step 2: categoryPath 파싱 ─────────────────────────────
        // 형식: "{majorCategoryName} > {minorCategoryName}"
        String[] categoryParts = correctedCategoryPath
                .split(REFGroceryCategoryPathSeparator.SEPARATOR_REGEX, 2);
        if (categoryParts.length < 2) {
            log.error("[카테고리 변경] categoryPath 형식 오류. path='{}'", correctedCategoryPath);
            return;
        }

        String majorCategoryName = categoryParts[0].trim();
        String minorCategoryName = categoryParts[1].trim();

        // ── Step 3: 카테고리 ID 조회 ──────────────────────────────
        Long newMajorCategoryId;
        Long newMinorCategoryId;
        try {
            newMajorCategoryId = categoryValidateService
                    .findMajorCategoryIdByName(majorCategoryName);
            newMinorCategoryId = categoryValidateService
                    .findMinorCategoryIdByName(minorCategoryName);
        } catch (IllegalArgumentException e) {
            log.error("[카테고리 변경] 카테고리 ID 조회 실패. major='{}', minor='{}', 사유: {}",
                    majorCategoryName, minorCategoryName, e.getMessage());
            return;
        }

        // ── Step 4: GroceryItem 조회 및 카테고리 변경 ─────────────
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

                            log.info("[카테고리 변경] GroceryItem 카테고리 갱신 완료. " +
                                            "groceryItemId={}, '{}' → '{}'",
                                    groceryItemId, correctedGroceryItemName, correctedCategoryPath);

                            // ── Step 5: Product 존재 여부 확인 및 처리 ────────────────
                            handleProductUpdate(event, groceryItemId,
                                    newMajorCategoryId, newMinorCategoryId);
                        },
                        () -> log.warn("[카테고리 변경] GroceryItem 없음. name='{}', reviewId={}",
                                correctedGroceryItemName, event.reviewId())
                );
    }

    /**
     * Product의 카테고리 참조를 업데이트하거나 신규 Product를 생성합니다.
     *
     * <p>
     * {@code origProductName + groceryItemId} 조합으로 Product를 조회합니다.
     * </p>
     * <ul>
     *   <li><b>Product 존재</b>: {@code REFGroceryItemReference}의 categoryId를 새 값으로 갱신</li>
     *   <li><b>Product 없음</b>: 수정된 GroceryItem 매핑으로 신규 Product upsert
     *       → 다음 recognition 시 ProductIndexSearch에서 우선 매칭</li>
     * </ul>
     *
     * @param event              카테고리 재분류 승인 이벤트 (origProductName, origBrandName 포함)
     * @param groceryItemId      변경된 GroceryItem ID
     * @param newMajorCategoryId 새 대분류 카테고리 ID
     * @param newMinorCategoryId 새 중분류 카테고리 ID
     */
    private void handleProductUpdate(REFCategoryReassignmentApprovedEvent event,
                                     Long groceryItemId,
                                     Long newMajorCategoryId,
                                     Long newMinorCategoryId) {

        String origProductName = event.origProductName();
        if (origProductName == null || origProductName.isBlank()) {
            log.debug("[카테고리 변경] origProductName 없음, Product 처리 스킵. reviewId={}",
                    event.reviewId());
            return;
        }

        boolean productExists = productRepository
                .existsByProductNameValueAndGroceryItemId(origProductName, groceryItemId);

        if (productExists) {
            // ── 기존 Product 카테고리 참조 갱신 ─────────────────────
            productRepository.findByProductNameAndGroceryItemId(origProductName, groceryItemId)
                    .ifPresent(product -> {
                        product.updateCategoryReference(newMajorCategoryId, newMinorCategoryId);
                        productRepository.save(product);
                        log.info("[카테고리 변경] Product 카테고리 참조 갱신. " +
                                        "productName='{}', groceryItemId={}",
                                origProductName, groceryItemId);
                    });

        } else {
            // ── 신규 Product 등록 ─────────────────────────────────────
            // 파이프라인 순서: ProductIndexSearch → GroceryItemDict → ML
            // 미리 등록해두면 다음 인식 시 ProductIndexSearch에서 우선 매칭
            productLifeCycleService.upsertProduct(
                    origProductName,
                    event.origBrandName(),
                    groceryItemId,
                    newMajorCategoryId,
                    newMinorCategoryId
            );
            log.info("[카테고리 변경] 신규 Product 등록. productName='{}', groceryItemId={}",
                    origProductName, groceryItemId);
        }
    }
}
