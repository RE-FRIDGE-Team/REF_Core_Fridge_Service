package com.refridge.core_server.recognition_feedback.domain.event;

/**
 * Product 자동 등록 조건이 충족되었을 때 발행되는 이벤트입니다.
 * <p>
 * 피드백 BC가 발행하고, Product BC의 이벤트 핸들러가 구독합니다.
 * <p>
 * 설계 의도:
 * <ul>
 *   <li>피드백 BC는 Product BC를 직접 의존하지 않습니다.</li>
 *   <li>groceryItemId만 포함 — majorCategoryId/minorCategoryId는 Product BC 핸들러가 직접 조회합니다.</li>
 *   <li>brandName은 파서가 추출한 값이므로 null일 수 있습니다.</li>
 * </ul>
 *
 * @param productName   파이프라인이 정제한 제품명 (orig_product_name)
 * @param brandName     파서가 추출한 브랜드명 (nullable)
 * @param groceryItemId 매핑된 식재료 ID — Product BC가 카테고리 조회에 사용
 * @param imageUrl      식재료 대표 이미지 URL (nullable)
 */
public record REFProductAutoRegistrationEvent(
        String productName,
        String brandName,
        Long groceryItemId,
        String imageUrl
) {
}