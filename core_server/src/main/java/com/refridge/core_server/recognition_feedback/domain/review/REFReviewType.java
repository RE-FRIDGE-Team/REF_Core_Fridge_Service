package com.refridge.core_server.recognition_feedback.domain.review;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 관리자 검수가 필요한 항목의 유형입니다.
 * <p>
 * 자동 반영하면 위험도가 높은 변경 사항들을 검수 큐에 적재할 때 사용합니다.
 */
@Getter
@RequiredArgsConstructor
public enum REFReviewType {

    /** 비식재료 사전에서 키워드 제거 요청 — 잘못 제거 시 비식재료가 식재료로 인식됨 */
    EXCLUSION_REMOVAL("비식재료 사전 키워드 제거", "EX"),

    /** GroceryItem 카테고리 변경 요청 — 영향 범위가 넓음 */
    CATEGORY_REASSIGNMENT("카테고리 재분류", "CA"),

    /** 신규 GroceryItem 생성 요청 — 사용자가 입력한 식재료명이 DB에 없는 경우 */
    NEW_GROCERY_ITEM("신규 식재료 등록", "GI"),

    /** 브랜드 사전 추가 요청 — 자동 추가 임계값 미도달이나 검수 필요한 경우 */
    BRAND_ADDITION("브랜드 사전 추가", "BR");

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