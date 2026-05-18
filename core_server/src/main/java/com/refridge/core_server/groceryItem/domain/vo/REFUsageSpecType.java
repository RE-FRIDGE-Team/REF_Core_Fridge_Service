package com.refridge.core_server.groceryItem.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 요리(Cook) 기능에서 사용자가 선택할 수 있는 사용량 표현 방식입니다.
 * fridge_server의 {@code UsageSpec sealed interface}와 1:1 대응합니다.
 *
 * <h3>매핑</h3>
 * <ul>
 *   <li>{@link #FULL}     ↔ {@code FullUsage}     — "전량" 사용</li>
 *   <li>{@link #COUNT}    ↔ {@code CountUsage}    — N개 (개수)</li>
 *   <li>{@link #QUANTITY} ↔ {@code QuantityUsage} — N g/ml/L 등 정량</li>
 *   <li>{@link #RATIO}    ↔ {@code RatioUsage}    — 1/4, 절반, 3/4 같은 분수</li>
 *   <li>{@link #SPOON}    ↔ {@code SpoonUsage}    — N스푼 / N티스푼</li>
 * </ul>
 *
 * <h3>디폴트 정책에서의 활용</h3>
 * <p>
 * {@link REFGroceryItemUsageProfile}이 카테고리(itemType)별로 어떤 UsageSpec을
 * 노출할지({@code allowedUsageTypes})와 첫 진입 시 활성 탭({@code defaultUsageType})을
 * 결정합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 9.
 */
@Getter
@RequiredArgsConstructor
public enum REFUsageSpecType {

    FULL("FULL"),
    COUNT("COUNT"),
    QUANTITY("QUANTITY"),
    RATIO("RATIO"),
    SPOON("SPOON");

    private final String code;

    public static REFUsageSpecType fromCode(String code) {
        return Optional.ofNullable(code)
                .flatMap(c -> Arrays.stream(values())
                        .filter(t -> t.code.equals(c))
                        .findAny())
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않은 UsageSpec 코드: " + code));
    }
}