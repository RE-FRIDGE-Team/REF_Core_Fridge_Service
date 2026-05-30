package com.refridge.core_server.recognition_feedback.infra.stream;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * fridge_server의 {@code FridgeFillCompletedEvent} JSON 페이로드를 core_server 측에서 받기 위한 record.
 *
 * <h2>왜 별도 record인가</h2>
 * 두 서버는 라이브러리를 공유하지 않으므로 record를 양쪽에 별도로 두고
 * <b>필드 이름만 JSON 계약(contract)으로 유지</b>한다.
 * 향후 fridge_server 측이 필드를 추가하더라도, core_server는 알고 있는 필드만 매핑한다.
 *
 * <h2>v4 변경점 (TODO 문서 v3 시점 → v4)</h2>
 * <ul>
 *   <li>fridge_server v4의 {@code FridgeFillCompletedEvent}가 VO 중첩 구조를 사용하므로
 *       payload도 그에 맞춰 {@link GroceryItemRefPayload}, {@link QuantityPayload},
 *       {@link MoneyPayload} 중첩 record 도입.</li>
 *   <li>{@code finalVolume}, {@code finalVolumeUnit} 별도 필드 제거 —
 *       v4는 {@link QuantityPayload#amount} + {@link QuantityPayload#unit}으로 통합.</li>
 *   <li>{@code userEditedFields}를 {@code Set<String>}으로 받음 —
 *       Set 중복 보장, enum 문자열로 직렬화된 값.</li>
 * </ul>
 *
 * <h2>알 수 없는 필드 무시</h2>
 * Jackson 3의 {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 설정으로
 * fridge_server가 새 필드를 추가해도 core_server는 무시한다 (forward compatibility).
 *
 * <h2>userEditedFields 값 카탈로그 (v4 기준)</h2>
 * <pre>
 * PRODUCT_NAME    — 제품명 변경
 * GROCERY_ITEM    — 식재료 자체 변경
 * CATEGORY        — 카테고리 변경
 * BRAND           — 브랜드 변경 또는 추가
 * QUANTITY_VOLUME — 수량 변경
 * </pre>
 * 알 수 없는 enum 문자열은 자연스럽게 무시된다 (mapping 단계에서 처리).
 *
 * @author 승훈
 * @since 2026-05-18
 */
public record REFFillEventPayload(
        String eventId,
        String memberId,
        UUID recognitionId,
        String fridgeItemId,
        GroceryItemRefPayload finalGroceryItemRef,
        String finalBrandName,
        QuantityPayload finalQuantity,
        MoneyPayload purchasePrice,
        Set<String> userEditedFields,
        Instant occurredAt
) {

    /**
     * 사용자가 한 가지 이상 수정했는지 여부.
     * Set이 비어있지 않으면 correctFeedback() 경로.
     */
    public boolean hasEdits() {
        return userEditedFields != null && !userEditedFields.isEmpty();
    }

    /**
     * 중첩 — fridge_server {@code GroceryItemRef} VO JSON 매핑.
     * v4 추가 필드(allowedUsageUnitsCsv, defaultUsageUnit, pieceWeightGram)도 포함하지만
     * Feedback BC는 현재 name과 category만 사용한다 (나머지는 forward compatibility용).
     */
    public record GroceryItemRefPayload(
            String groceryItemId,
            String productId,
            String name,
            String category,
            String defaultUnit,
            Integer minPortionAmount,
            Integer maxPortionAmount,
            String allowedUsageUnitsCsv,
            String defaultUsageUnit,
            Integer pieceWeightGram
    ) {}

    /**
     * 중첩 — fridge_server {@code Quantity} VO JSON 매핑.
     * <h3>unit 값 카탈로그</h3>
     * G / KG / ML / L / EA / PACK / PIECE / SERVING (8개 enum 이름)
     */
    public record QuantityPayload(
            BigDecimal amount,
            String unit
    ) {}

    /**
     * 중첩 — fridge_server {@code Money} VO JSON 매핑.
     * scale=0 (원 단위, 소수 없음).
     */
    public record MoneyPayload(
            BigDecimal amount
    ) {}
}