package com.refridge.core_server.recognition_feedback.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 관리자가 카테고리 재분류 검수 항목을 승인했을 때 발행되는 도메인 이벤트입니다.
 *
 * <h3>발행 경로</h3>
 * <pre>
 *   관리자 승인 요청
 *     → REFReviewAdminService.approveReviewItem()
 *         → REFFeedbackReviewItem.approve()
 *         → REFCategoryReassignmentApprovedEvent 발행
 *             → 해당 EventHandler (Async)
 *                   ① GroceryItem 카테고리 변경
 *                   ② Product 존재 → updateCategoryReference()
 *                      Product 없음  → upsertProduct() (다음 recognition 대비)
 * </pre>
 *
 * <h3>targetValue 형식</h3>
 * <pre>
 *   "{correctedGroceryItemName}::{correctedCategoryPath}"
 *   예: "두부::채소류 > 두부/묵류"
 * </pre>
 *
 * <h3>origProductName 용도</h3>
 * <p>
 * 핸들러가 Product BC에서 해당 원본 제품명으로 등록된 Product 존재 여부를 확인합니다.
 * Product가 존재하면 비정규화된 카테고리 참조를 업데이트하고,
 * 없으면 수정된 GroceryItem 매핑으로 신규 Product를 생성합니다.
 * 신규 Product를 미리 등록해두면 다음 인식 시 ProductIndexSearch 단계에서
 * GroceryItemDict/ML 이전에 정확하게 매칭되어 오분류를 예방합니다.
 * </p>
 *
 * <h3>Cross-Boundary 이벤트 — Step 7 Outbox 적용 대상 (F3)</h3>
 * <p>
 * 이 이벤트는 Feedback BC에서 발행되어 GroceryItem BC + Product BC 두 곳이 구독하는
 * <b>BC 경계 외부 이벤트</b>입니다.
 * {@link REFCrossBoundaryFeedbackEvent} 마커를 구현하여
 * {@code REFFeedbackOutboxAppender}가 자동 적재하고,
 * Relayer가 {@code feedback:category-reassigned} 스트림에 발행합니다.
 * </p>
 *
 * <h3>aggregateId 선정 근거</h3>
 * <p>
 * {@code reviewId}가 검수 항목의 PK이며, 같은 검수가 두 번 승인되는 경우는 없으므로
 * 멱등성 추적의 자연 키로 적합합니다.
 * </p>
 *
 * @param eventId          이벤트 고유 ID (UUID 문자열) — Outbox row PK, 멱등성 키
 * @param aggregateId      AR 식별자 — {@code String.valueOf(reviewId)}
 * @param occurredAt       이벤트 발생 시각
 * @param reviewId         승인된 검수 항목 ID
 * @param targetValue      "{correctedGroceryItemName}::{correctedCategoryPath}" 형식
 * @param origProductName  원본 인식 제품명 (Product 존재 여부 확인용, nullable)
 * @param origBrandName    원본 브랜드명 (신규 Product 생성 시 사용, nullable)
 * @param sourceFeedbackId 최초 발생 피드백 ID
 *
 * @author 이승훈
 * @since 2026. 4. 5. (Step 7 marker 적용: 2026. 5. 30.)
 */
public record REFCategoryReassignmentApprovedEvent(
        String eventId,
        String aggregateId,
        Instant occurredAt,
        Long reviewId,
        String targetValue,
        String origProductName,
        String origBrandName,
        UUID sourceFeedbackId
) implements REFCrossBoundaryFeedbackEvent {

    /**
     * 발행 측이 기존 시그니처처럼 호출할 수 있도록 제공하는 정적 팩토리.
     * <ul>
     *   <li>{@code eventId} — 새 UUID 자동 생성</li>
     *   <li>{@code aggregateId} — {@code String.valueOf(reviewId)}</li>
     *   <li>{@code occurredAt} — 호출 시점</li>
     * </ul>
     */
    public static REFCategoryReassignmentApprovedEvent of(
            Long reviewId,
            String targetValue,
            String origProductName,
            String origBrandName,
            UUID sourceFeedbackId
    ) {
        return new REFCategoryReassignmentApprovedEvent(
                UUID.randomUUID().toString(),
                String.valueOf(reviewId),
                Instant.now(),
                reviewId,
                targetValue,
                origProductName,
                origBrandName,
                sourceFeedbackId
        );
    }
}