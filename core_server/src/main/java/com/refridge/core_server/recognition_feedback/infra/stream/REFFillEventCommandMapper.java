package com.refridge.core_server.recognition_feedback.infra.stream;

import com.refridge.core_server.recognition_feedback.application.dto.command.REFFeedbackApproveCommand;
import com.refridge.core_server.recognition_feedback.application.dto.command.REFFeedbackCorrectCommand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * fridge_server v4 페이로드를 Feedback BC의 기존 Command로 변환.
 *
 * <h2>v3 TODO → v4 매핑 변경점</h2>
 * <table border=1>
 *   <tr><th>v3 TODO 가정</th><th>v4 실제 payload</th><th>매핑</th></tr>
 *   <tr><td>{@code finalGroceryItemName}</td><td>{@code finalGroceryItemRef.name}</td><td>중첩 객체 펼치기</td></tr>
 *   <tr><td>{@code finalCategoryPath}</td><td>{@code finalGroceryItemRef.category} (enum 이름)</td><td>enum 문자열 그대로</td></tr>
 *   <tr><td>{@code finalQuantity (Integer)}</td><td>{@code finalQuantity.amount (BigDecimal)}</td><td>{@code intValue()} 변환</td></tr>
 *   <tr><td>{@code finalVolume, finalVolumeUnit}</td><td>(v4에 없음 — Quantity 단일 개념)</td><td>두 필드 모두 {@code null} 전달</td></tr>
 *   <tr><td>{@code purchasePrice (Long)}</td><td>{@code purchasePrice.amount (BigDecimal)}</td><td>{@code longValue()} 변환</td></tr>
 *   <tr><td>{@code correctedProductName}</td><td>(fridge가 보낼 의무 없음)</td><td>{@code null} — Recognition 스냅샷 유지</td></tr>
 * </table>
 *
 * <h2>null-safe 변환</h2>
 * fridge_server 측 객체가 nullable이거나 페이로드에서 누락된 경우를 모두 방어한다.
 * {@code Optional.ofNullable} 체이닝으로 NPE 방지.
 *
 * @author 승훈
 * @since 2026-05-18
 */
@Component
public class REFFillEventCommandMapper {

    public REFFeedbackApproveCommand toApproveCommand(REFFillEventPayload p) {
        return REFFeedbackApproveCommand.builder()
                .recognitionId(p.recognitionId())
                .purchasePrice(extractPurchasePrice(p))
                .build();
    }

    public REFFeedbackCorrectCommand toCorrectCommand(REFFillEventPayload p) {
        return REFFeedbackCorrectCommand.builder()
                .recognitionId(p.recognitionId())

                // Recognition 스냅샷의 productName 유지 — fridge가 보내지 않음
                .correctedProductName(null)

                // GroceryItemRef 중첩 객체에서 name·category 펼침
                .correctedGroceryItemName(extractGroceryItemName(p))
                .correctedCategoryPath(extractCategoryPath(p))

                .correctedBrandName(p.finalBrandName())

                // v4: Quantity 단일 개념 → correctedQuantity만 채움
                .correctedQuantity(extractQuantityAsInt(p))

                // v4에 volume 개념 없음 — 두 필드 모두 null
                .correctedVolume(null)
                .correctedVolumeUnit(null)

                // Money VO → Long
                .purchasePrice(extractPurchasePrice(p))
                .build();
    }

    // ── 추출 헬퍼 ─────────────────────────────────────────────────

    private String extractGroceryItemName(REFFillEventPayload p) {
        return Optional.ofNullable(p.finalGroceryItemRef())
                .map(REFFillEventPayload.GroceryItemRefPayload::name)
                .orElse(null);
    }

    private String extractCategoryPath(REFFillEventPayload p) {
        return Optional.ofNullable(p.finalGroceryItemRef())
                .map(REFFillEventPayload.GroceryItemRefPayload::category)
                .orElse(null);
    }

    /**
     * BigDecimal 수량을 Integer로 변환.
     * 소수점이 있다면 절삭(intValue) — 수량은 정수 단위가 자연스러움.
     */
    private Integer extractQuantityAsInt(REFFillEventPayload p) {
        return Optional.ofNullable(p.finalQuantity())
                .map(REFFillEventPayload.QuantityPayload::amount)
                .map(BigDecimal::intValue)
                .orElse(null);
    }

    /**
     * Money VO의 BigDecimal을 Long(원)으로 변환.
     */
    private Long extractPurchasePrice(REFFillEventPayload p) {
        return Optional.ofNullable(p.purchasePrice())
                .map(REFFillEventPayload.MoneyPayload::amount)
                .map(BigDecimal::longValue)
                .orElse(null);
    }
}