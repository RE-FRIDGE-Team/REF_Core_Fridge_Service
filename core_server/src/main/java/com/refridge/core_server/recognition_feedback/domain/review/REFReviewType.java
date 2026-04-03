package com.refridge.core_server.recognition_feedback.domain.review;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 관리자 검수가 필요한 항목의 유형입니다.
 *
 * <h3>브랜드 추가 제거 이유</h3>
 * 브랜드명은 사용자가 직접 입력하는 필드로, 동일 브랜드를 여러 사용자가
 * 독립적으로 입력했다는 사실 자체가 검증입니다.
 * correctionSuggestions에 노출되지 않아 악용 위험이 낮으므로
 * 자동 반영(REFBrandDictionaryFlushService)으로 처리합니다.
 */
@Getter
@RequiredArgsConstructor
public enum REFReviewType {

    /** 비식재료 사전에서 키워드 제거 요청 — 잘못 제거 시 비식재료가 식재료로 인식됨 */
    EXCLUSION_REMOVAL("비식재료 사전 키워드 제거", "EX"),

    /** GroceryItem 카테고리 변경 요청 — 영향 범위가 넓음 */
    CATEGORY_REASSIGNMENT("카테고리 재분류", "CA"),

    /** 신규 GroceryItem 생성 요청 — 사용자가 입력한 식재료명이 DB에 없는 경우 */
    NEW_GROCERY_ITEM("신규 식재료 등록", "GI");

    private final String description;
    private final String dbCode;

    public static REFReviewType fromDbCode(String dbCode) {
        return Arrays.stream(REFReviewType.values())
                .filter(t -> t.dbCode.equals(dbCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않은 검수 유형 코드입니다: " + dbCode));
    }
}