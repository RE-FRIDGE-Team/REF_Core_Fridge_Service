package com.refridge.core_server.recognition_feedback.infra.outbox;

import com.refridge.core_server.recognition_feedback.domain.event.REFCrossBoundaryFeedbackEvent;
import org.springframework.stereotype.Component;

/**
 * 이벤트 클래스 → Redis Stream Key 매핑 컴포넌트.
 *
 * <h2>역할</h2>
 * Outbox Appender가 이벤트 타입별 stream key 결정 로직을 단일 책임으로 분리.
 * 향후 새 cross-boundary 이벤트 추가 시 이 클래스만 수정하면 된다.
 *
 * <h2>매핑표</h2>
 * <pre>
 * REFProductFeedbackAggregationEvent    → feedback:product-aggregation
 * REFCategoryReassignmentApprovedEvent  → feedback:category-reassigned
 * REFGroceryItemCorrectionConfirmedEvent → feedback:grocery-item-correction-confirmed
 * </pre>
 *
 * <h2>왜 클래스명 기반 매핑인가</h2>
 * {@code event.getClass().getSimpleName()}이 안정적이고 명시적이라 case문이 직관적.
 * Spring AOP 프록시가 적용된 클래스도 정상 매핑된다 (record는 final이라 proxy 대상 아님).
 *
 * @author 승훈
 * @since 2026-05-18
 */
@Component
public class REFOutboxStreamKeyResolver {

    public String resolve(Class<? extends REFCrossBoundaryFeedbackEvent> eventClass) {
        String simpleName = eventClass.getSimpleName();
        return switch (simpleName) {
            case "REFProductFeedbackAggregationEvent"    -> "feedback:product-aggregation";
            case "REFCategoryReassignmentApprovedEvent"  -> "feedback:category-reassigned";
            case "REFGroceryItemCorrectionConfirmedEvent" -> "feedback:grocery-item-correction-confirmed";
            default -> throw new IllegalArgumentException(
                    "알 수 없는 Cross-Boundary 이벤트 — Stream Key 매핑 누락: " + simpleName);
        };
    }
}