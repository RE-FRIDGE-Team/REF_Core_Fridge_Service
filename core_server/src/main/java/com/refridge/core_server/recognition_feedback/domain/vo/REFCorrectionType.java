package com.refridge.core_server.recognition_feedback.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자가 수정한 필드의 유형을 나타냅니다.
 * {@link REFCorrectionDiff}에서 변경된 필드 목록을 반환할 때 사용됩니다.
 * <p>
 * 부정 피드백 이벤트 핸들러는 이 유형을 기반으로
 * 사전 보강, alias 등록 등 개선 액션을 분기합니다.
 */
@Getter
@RequiredArgsConstructor
public enum REFCorrectionType {

    /** 정제된 제품명이 변경됨 → alias 매핑 사전 추가 후보 */
    PRODUCT_NAME("제품명 변경"),

    /** 식재료명이 변경됨 → GroceryItem 매핑 재학습 */
    GROCERY_ITEM("식재료명 변경"),

    /** 카테고리가 변경됨 → 카테고리 분류 재검토 */
    CATEGORY("카테고리 변경"),

    /** 브랜드명이 변경됨 → 브랜드 사전 추가 후보 */
    BRAND("브랜드 변경"),

    /** 수량 또는 용량이 변경됨 → 파서 정규식 보완 후보 */
    QUANTITY_VOLUME("수량/용량 변경"),

    /** 비식재료로 반려되었으나 사용자가 식재료로 수정함 → 비식재료 사전 수정 후보 */
    REJECTED_BUT_FOOD("비식재료 반려 → 식재료 수정");

    private final String description;
}