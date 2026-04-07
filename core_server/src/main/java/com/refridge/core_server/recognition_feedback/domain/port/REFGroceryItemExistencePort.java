package com.refridge.core_server.recognition_feedback.domain.port;

import com.refridge.core_server.recognition_feedback.infra.event.improvement.REFExclusionRemovalHandler;

/**
 * 피드백 BC가 식재료 존재 여부를 확인하기 위한 포트입니다.
 *
 * <h3>사용 목적</h3>
 * <p>
 * {@link REFExclusionRemovalHandler}의
 * Gate 3에서 사용자가 입력한 {@code correctedGroceryItemName}이
 * GroceryItem DB에 실제 존재하는지 확인합니다.
 * </p>
 *
 * <p>
 * 존재하지 않는 식재료명으로 정정한 피드백은 신뢰할 수 없으므로
 * 자동 제거 대상에서 제외하고 관리자 수동 검수로 처리합니다.
 * </p>
 *
 * <h3>구현 위치</h3>
 * <p>
 * GroceryItem Context의 Repository를 사용하는 Adapter가 구현합니다.
 * ({@code REFGroceryItemExistenceAdapter})
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 6.
 * @see REFExclusionRemovalHandler
 */
public interface REFGroceryItemExistencePort {

    /**
     * 식재료명이 GroceryItem DB에 존재하는지 확인합니다.
     *
     * @param groceryItemName 확인할 식재료명
     * @return 존재하면 {@code true}, 없으면 {@code false}
     */
    boolean existsByName(String groceryItemName);
}