package com.refridge.core_server.groceryItem.domain.service;

import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemUsageMetadata;
import com.refridge.core_server.groceryItem.domain.vo.REFUsageUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GroceryItem 품목명에 기반한 특수 단위 오버라이드를 적용하는 도메인 서비스입니다.
 *
 * <h3>역할</h3>
 * <p>
 * 카테고리 프로필이 결정한 기본 메타데이터에 대해, 품목명 키워드 매칭으로
 * 단위를 추가/조정하는 보조 로직을 담당합니다. 데이터 테이블로 분리하지 않고
 * 패턴 매칭으로 처리하여 새 케이스 추가 시 코드 리뷰를 거치도록 합니다.
 * </p>
 *
 * <h3>적용 예시</h3>
 * <ul>
 *   <li>"두부" 포함 품목 → TOFU_BLOCK(모) 단위 추가</li>
 *   <li>"슬라이스" + "치즈" 포함 → CHEESE_SLICE(장) 단위 추가</li>
 *   <li>"고등어", "갈치", "삼치", "꽁치" 등 일반 어종 → FISH(마리) 단위 추가</li>
 *   <li>"통조림", "캔" 포함 품목 → CAN(캔) 단위 추가</li>
 * </ul>
 *
 * <h3>위치 — 도메인 서비스</h3>
 * <p>
 * GroceryItem AR이 직접 품목명 패턴을 매칭하기엔 책임이 비대해지고,
 * 카테고리 정책과의 결합도 강해집니다. 또한 새 케이스 추가 시 AR이 수정되어야 하므로
 * 외부 도메인 서비스로 분리합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Slf4j
@Component
public class REFGroceryItemSpecialCaseResolver {

    private static final List<String> COMMON_FISH_NAMES = List.of(
            "고등어", "갈치", "삼치", "꽁치", "조기", "명태", "동태", "도미", "방어", "광어");

    /**
     * 기본 메타데이터에 품목명 기반 오버라이드를 적용합니다.
     *
     * @param groceryItemName 식재료 품목명
     * @param baseMetadata    카테고리 프로필이 결정한 기본 메타데이터
     * @return 오버라이드가 적용된 새 메타데이터 (적용할 케이스 없으면 baseMetadata 그대로)
     */
    public REFGroceryItemUsageMetadata applyOverrides(
            String groceryItemName, REFGroceryItemUsageMetadata baseMetadata) {

        if (groceryItemName == null || groceryItemName.isBlank() || baseMetadata == null) {
            return baseMetadata;
        }

        REFGroceryItemUsageMetadata result = baseMetadata;

        // [Case 1] 두부 — TOFU_BLOCK 추가
        if (containsAny(groceryItemName, "두부")) {
            result = result.appendAllowedUsageUnits(REFUsageUnit.TOFU_BLOCK);
            log.debug("[특수 케이스] 두부 패턴 매칭 → TOFU_BLOCK 추가. name='{}'", groceryItemName);
        }

        // [Case 2] 슬라이스 치즈 — CHEESE_SLICE 추가 (치즈 프로필엔 이미 있지만 강조)
        if (containsAll(groceryItemName, "슬라이스", "치즈")) {
            result = result.appendAllowedUsageUnits(REFUsageUnit.CHEESE_SLICE);
            log.debug("[특수 케이스] 슬라이스 치즈 패턴 매칭 → CHEESE_SLICE 추가. name='{}'", groceryItemName);
        }

        // [Case 3] 일반적 어종 — FISH 추가
        if (containsAny(groceryItemName, COMMON_FISH_NAMES.toArray(new String[0]))) {
            result = result.appendAllowedUsageUnits(REFUsageUnit.FISH);
            log.debug("[특수 케이스] 일반 어종 패턴 매칭 → FISH 추가. name='{}'", groceryItemName);
        }

        // [Case 4] 통조림·캔 — CAN 추가
        if (containsAny(groceryItemName, "통조림", "캔")) {
            result = result.appendAllowedUsageUnits(REFUsageUnit.CAN);
            log.debug("[특수 케이스] 통조림/캔 패턴 매칭 → CAN 추가. name='{}'", groceryItemName);
        }

        return result;
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static boolean containsAll(String text, String... keywords) {
        for (String kw : keywords) {
            if (!text.contains(kw)) return false;
        }
        return true;
    }
}