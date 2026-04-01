package com.refridge.core_server.recognition_feedback.domain.event;

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
 * @param productName   파이프라인이 정제한 제품명
 * @param brandName     파서가 추출한 브랜드명 (nullable)
 * @param groceryItemId 매핑된 식재료 ID
 * @param imageUrl      식재료 대표 이미지 URL (nullable)
 */
public record REFProductFeedbackAggregationEvent(
        String productName,
        String brandName,
        Long groceryItemId,
        String imageUrl
) {
}