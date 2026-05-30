package com.refridge.core_server.recognition_feedback.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 피드백 집계 조건이 충족되어 Product 등록이 필요할 때 발행되는 이벤트입니다.
 * <p>
 * 피드백 BC가 발행하고, Product BC의 핸들러가 구독합니다.
 *
 * <h3>발행 조건</h3>
 * 긍정 피드백이 우세하여 가중 점수 임계값을 충족한 경우.
 * 즉, 파이프라인의 인식 결과가 대다수 사용자에게 올바르다고 확인된 상태입니다.
 *
 * <h3>등록 값의 출처</h3>
 * 파이프라인이 인식한 값을 그대로 사용합니다.
 * 긍정 피드백 우세 = 인식 결과가 맞다는 사용자 확인이므로,
 * 부정 피드백의 수정 이력을 반영하지 않습니다.
 *
 * <h3>카테고리 ID 미포함 이유</h3>
 * majorCategoryId/minorCategoryId는 이벤트에 포함하지 않습니다.
 * Product BC 핸들러가 groceryItemId로 직접 조회하여 채웁니다.
 * 피드백 BC가 GroceryItem 구조를 알 필요가 없도록 책임을 분리합니다.
 *
 * <h3>Cross-Boundary 이벤트 — Step 7 Outbox 적용 대상 (F3)</h3>
 * <p>
 * 이 이벤트는 Feedback BC에서 발행되어 Product BC가 구독하는
 * <b>BC 경계 외부 이벤트</b>입니다.
 * {@link REFCrossBoundaryFeedbackEvent} 마커를 구현하여
 * {@code REFFeedbackOutboxAppender}가 자동 적재하고,
 * Relayer가 {@code feedback:product-aggregation} 스트림에 발행합니다.
 * </p>
 *
 * <h3>aggregateId 선정 근거</h3>
 * <p>
 * Product의 자연 키는 ({@code productName}, {@code brandName}) 조합입니다.
 * 두 값을 콜론으로 합쳐서 aggregateId로 사용합니다.
 * brandName이 null인 경우 빈 문자열로 처리하여 키 충돌을 방지합니다.
 * </p>
 *
 * @param eventId       이벤트 고유 ID (UUID 문자열) — Outbox row PK, 멱등성 키
 * @param aggregateId   AR 식별자 — {@code productName + ":" + brandName} (Product 자연 키)
 * @param occurredAt    이벤트 발생 시각
 * @param productName   파이프라인이 정제한 제품명
 * @param brandName     파서가 추출한 브랜드명 (nullable)
 * @param groceryItemId 매핑된 식재료 ID
 * @param imageUrl      식재료 대표 이미지 URL (nullable)
 *
 * @since 2026. 4. 3. (Step 7 marker 적용: 2026. 5. 30.)
 */
public record REFProductFeedbackAggregationEvent(
        String eventId,
        String aggregateId,
        Instant occurredAt,
        String productName,
        String brandName,
        Long groceryItemId,
        String imageUrl
) implements REFCrossBoundaryFeedbackEvent {

    /**
     * 발행 측이 기존 시그니처처럼 호출할 수 있도록 제공하는 정적 팩토리.
     * <ul>
     *   <li>{@code eventId} — 새 UUID 자동 생성</li>
     *   <li>{@code aggregateId} — {@code productName + ":" + (brandName != null ? brandName : "")}</li>
     *   <li>{@code occurredAt} — 호출 시점</li>
     * </ul>
     */
    public static REFProductFeedbackAggregationEvent of(
            String productName,
            String brandName,
            Long groceryItemId,
            String imageUrl
    ) {
        String aggregateId = productName + ":" + (brandName != null ? brandName : "");
        return new REFProductFeedbackAggregationEvent(
                UUID.randomUUID().toString(),
                aggregateId,
                Instant.now(),
                productName,
                brandName,
                groceryItemId,
                imageUrl
        );
    }
}