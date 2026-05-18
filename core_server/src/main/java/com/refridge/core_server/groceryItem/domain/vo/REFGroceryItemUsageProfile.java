package com.refridge.core_server.groceryItem.domain.vo;

import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * {@link REFInventoryItemType}별로 사용량 메타데이터의 <b>카테고리 디폴트 정책</b>을 정의합니다.
 *
 * <h3>역할</h3>
 * <p>
 * RE:FRIDGE의 식재료 ~3,500–5,000건을 일일이 수동 매핑하지 않기 위한
 * 1차 자동 채움 규칙입니다. 카테고리만 봐도 결정 가능한 약 70%의 식재료를
 * 이 프로필로 한 번에 처리합니다.
 * </p>
 *
 * <h3>커버리지 계층</h3>
 * <pre>
 *   [Layer 1] 카테고리 규칙 (이 enum)        — 약 70% 자동 채움 ← 현재 작업 범위
 *   [Layer 2] LLM 기반 환산값 추정 (TODO)    — pieceWeightGram, spoonVolumeMl 미세조정
 *   [Layer 3] 사용자 데이터 학습 (TODO)      — 누적 입력 기반 자가 보정
 * </pre>
 *
 * <h3>설계 원칙</h3>
 * <ol>
 *   <li>각 프로필은 {@code allowedUsageTypes}, {@code defaultUsageType}, {@code defaultUnit},
 *       {@code defaultSpoonVolumeMl} (선택)을 보유합니다.</li>
 *   <li>{@code pieceWeightGram}은 카테고리만으로 결정 불가 → Layer 2/3에서 채움.</li>
 *   <li>spoonVolumeMl 권장값은 SAUCE_SEASONING 계열에 한해 15ml(액상 기준)를 기본값으로 사용합니다.
 *       구체 값(가루 8, 꿀 21 등)은 LLM 보정 단계에서 조정합니다.</li>
 * </ol>
 *
 * <h3>UI 매핑 예시</h3>
 * <ul>
 *   <li>{@code SAUCE_SEASONING} → [SPOON, QUANTITY] / 기본 SPOON / ML / 15ml</li>
 *   <li>{@code RAW_MEAT} → [QUANTITY, RATIO] / 기본 QUANTITY / G</li>
 *   <li>{@code DAIRY} → [COUNT, QUANTITY] / 기본 COUNT / ML</li>
 *   <li>{@code GRAIN} → [RATIO, QUANTITY] / 기본 RATIO / G  (사진의 "쌀" 케이스)</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 9.
 */
@Getter
public enum REFGroceryItemUsageProfile {

    // ─── 알코올 ───
    ALCOHOL_PROFILE(
            REFInventoryItemType.ALCOHOL,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.QUANTITY),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.ML,
            null
    ),

    // ─── 채소·과일 (생물) ───
    RAW_PRODUCE_PROFILE(
            REFInventoryItemType.RAW_PRODUCE,
            List.of(REFUsageSpecType.RATIO, REFUsageSpecType.QUANTITY, REFUsageSpecType.COUNT),
            REFUsageSpecType.RATIO,
            REFGroceryItemDefaultUnit.G,
            null
    ),
    RAW_FRUIT_PROFILE(
            REFInventoryItemType.RAW_FRUIT,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.RATIO, REFUsageSpecType.QUANTITY),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.G,
            null
    ),

    // ─── 곡물·간단 가공 ───
    GRAIN_PROFILE(
            REFInventoryItemType.GRAIN,
            List.of(REFUsageSpecType.RATIO, REFUsageSpecType.QUANTITY),
            REFUsageSpecType.RATIO,
            REFGroceryItemDefaultUnit.G,
            null
    ),
    SIMPLE_PROCESSED_PROFILE(
            REFInventoryItemType.SIMPLE_PROCESSED,
            List.of(REFUsageSpecType.RATIO, REFUsageSpecType.QUANTITY, REFUsageSpecType.COUNT),
            REFUsageSpecType.RATIO,
            REFGroceryItemDefaultUnit.G,
            null
    ),

    // ─── 육류 ───
    RAW_MEAT_PROFILE(
            REFInventoryItemType.RAW_MEAT,
            List.of(REFUsageSpecType.QUANTITY, REFUsageSpecType.RATIO),
            REFUsageSpecType.QUANTITY,
            REFGroceryItemDefaultUnit.G,
            null
    ),
    PROCESSED_MEAT_PROFILE(
            REFInventoryItemType.PROCESSED_MEAT,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.QUANTITY, REFUsageSpecType.RATIO),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.G,
            null
    ),

    // ─── 수산물 ───
    RAW_SEAFOOD_PROFILE(
            REFInventoryItemType.RAW_SEAFOOD,
            List.of(REFUsageSpecType.QUANTITY, REFUsageSpecType.RATIO, REFUsageSpecType.COUNT),
            REFUsageSpecType.QUANTITY,
            REFGroceryItemDefaultUnit.G,
            null
    ),
    DRIED_SEAFOOD_PROFILE(
            REFInventoryItemType.DRIED_SEAFOOD,
            List.of(REFUsageSpecType.QUANTITY, REFUsageSpecType.RATIO),
            REFUsageSpecType.QUANTITY,
            REFGroceryItemDefaultUnit.G,
            null
    ),

    // ─── 즉석·가공 식품 ───
    RTC_MEAL_PROFILE(
            REFInventoryItemType.RTC_MEAL,
            List.of(REFUsageSpecType.FULL, REFUsageSpecType.COUNT),
            REFUsageSpecType.FULL,
            REFGroceryItemDefaultUnit.EA,
            null
    ),
    INSTANT_PROFILE(
            REFInventoryItemType.INSTANT,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.FULL),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.EA,
            null
    ),
    FROZEN_MEAL_PROFILE(
            REFInventoryItemType.FROZEN_MEAL,
            List.of(REFUsageSpecType.FULL, REFUsageSpecType.COUNT, REFUsageSpecType.RATIO),
            REFUsageSpecType.FULL,
            REFGroceryItemDefaultUnit.EA,
            null
    ),

    // ─── 양념·소스·향신료 (스푼 단위 핵심 카테고리) ───
    SAUCE_SEASONING_PROFILE(
            REFInventoryItemType.SAUCE_SEASONING,
            List.of(REFUsageSpecType.SPOON, REFUsageSpecType.QUANTITY),
            REFUsageSpecType.SPOON,
            REFGroceryItemDefaultUnit.ML,    // 액상 기본 — 가루는 LLM 보정 단계에서 G로 변경
            15                                // 1 TABLESPOON = 15ml (액상 표준)
    ),

    // ─── 음료·유제품 ───
    BEVERAGE_PROFILE(
            REFInventoryItemType.BEVERAGE,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.QUANTITY),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.ML,
            null
    ),
    DAIRY_PROFILE(
            REFInventoryItemType.DAIRY,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.QUANTITY, REFUsageSpecType.RATIO),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.ML,
            null
    ),

    // ─── 기타 가공 식품 ───
    BAKERY_PROFILE(
            REFInventoryItemType.BAKERY,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.RATIO, REFUsageSpecType.QUANTITY),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.G,
            null
    ),
    SNACK_PROFILE(
            REFInventoryItemType.SNACK,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.FULL),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.EA,
            null
    ),
    CANNED_PROFILE(
            REFInventoryItemType.CANNED,
            List.of(REFUsageSpecType.COUNT, REFUsageSpecType.FULL, REFUsageSpecType.QUANTITY),
            REFUsageSpecType.COUNT,
            REFGroceryItemDefaultUnit.G,
            null
    );

    private final REFInventoryItemType itemType;
    private final List<REFUsageSpecType> allowedUsageTypes;
    private final REFUsageSpecType defaultUsageType;
    private final REFGroceryItemDefaultUnit defaultUnit;
    private final Integer defaultSpoonVolumeMl; // SAUCE_SEASONING만 사용, 나머지는 null

    REFGroceryItemUsageProfile(
            REFInventoryItemType itemType,
            List<REFUsageSpecType> allowedUsageTypes,
            REFUsageSpecType defaultUsageType,
            REFGroceryItemDefaultUnit defaultUnit,
            Integer defaultSpoonVolumeMl) {

        this.itemType = itemType;
        this.allowedUsageTypes = List.copyOf(allowedUsageTypes);
        this.defaultUsageType = defaultUsageType;
        this.defaultUnit = defaultUnit;
        this.defaultSpoonVolumeMl = defaultSpoonVolumeMl;

        if (!allowedUsageTypes.contains(defaultUsageType)) {
            throw new IllegalStateException(
                    "defaultUsageType은 allowedUsageTypes에 포함되어야 합니다: " + this.name());
        }
    }

    /**
     * 주어진 itemType에 매핑되는 프로필을 반환합니다.
     * 매칭되는 프로필이 없으면 빈 Optional.
     */
    public static Optional<REFGroceryItemUsageProfile> findByItemType(REFInventoryItemType itemType) {
        return Optional.ofNullable(itemType)
                .flatMap(t -> Arrays.stream(values())
                        .filter(p -> p.itemType == t)
                        .findFirst());
    }

    /**
     * 이 프로필을 그대로 적용한 메타데이터 VO를 빌드합니다.
     * pieceWeightGram, portion 범위는 카테고리만으로 결정 불가 → null로 둡니다.
     */
    public REFGroceryItemUsageMetadata toMetadata() {
        return REFGroceryItemUsageMetadata.of(
                this.defaultUnit,
                null,                               // minPortionAmount — Layer 2/3에서 채움
                null,                               // maxPortionAmount — Layer 2/3에서 채움
                this.defaultSpoonVolumeMl,
                null,                               // pieceWeightGram — Layer 2/3에서 채움 (TODO)
                this.allowedUsageTypes,
                this.defaultUsageType
        );
    }
}