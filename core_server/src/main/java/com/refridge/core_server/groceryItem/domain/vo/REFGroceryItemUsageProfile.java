package com.refridge.core_server.groceryItem.domain.vo;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.refridge.core_server.groceryItem.domain.vo.REFUsageUnit.*;

/**
 * 카테고리 이름(중분류 또는 대분류) 기반으로 사용량 메타데이터 디폴트 정책을 정의하는 enum입니다.
 *
 * <h3>매핑 알고리즘</h3>
 * <ol>
 *   <li>먼저 GroceryItem의 <b>중분류 categoryName</b>으로 {@link Level#MINOR} 프로필 검색</li>
 *   <li>못 찾으면 <b>대분류 categoryName</b>으로 {@link Level#MAJOR} 프로필 검색</li>
 *   <li>그래도 못 찾으면 매핑 실패 (empty 메타데이터 유지)</li>
 * </ol>
 *
 * <h3>특수 케이스 처리</h3>
 * <p>
 * 카테고리 프로필 적용 후 {@code REFGroceryItemSpecialCaseResolver}가 품목명 기반으로
 * 단위를 추가/조정합니다 (예: "두부" 포함 품목명에 TOFU_BLOCK 추가).
 * </p>
 *
 * <h3>defaultUnit 규약</h3>
 * <p>
 * 각 프로필이 보유한 {@code defaultUnit}은 반드시 {@link REFUsageUnit#isBaseUnit()}가 true여야 합니다.
 * 컴파일 시점 검증은 불가하므로 생성자에서 런타임 검증합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Getter
public enum REFGroceryItemUsageProfile {

    /* ───────────── MAJOR 카테고리 프로필 (대분류 fallback) ───────────── */
    MAJOR_술        ("술",      Level.MAJOR, MILLILITER, List.of(CUP, MILLILITER, LITER, RATIO),       CUP),
    MAJOR_음료      ("음료",    Level.MAJOR, MILLILITER, List.of(PIECE, MILLILITER, LITER),            PIECE),
    MAJOR_간식류    ("간식류",  Level.MAJOR, PIECE,      List.of(PIECE),                              PIECE),

    /* ───────────── MINOR 카테고리 프로필 (중분류) ───────────── */
    // 채소·버섯·곡물·면류
    MINOR_채소      ("채소",     Level.MINOR, GRAM, List.of(GRAM, PIECE),               GRAM),
    MINOR_버섯      ("버섯",     Level.MINOR, GRAM, List.of(GRAM, PIECE),               GRAM),
    MINOR_쌀잡곡    ("쌀/잡곡",  Level.MINOR, GRAM, List.of(CUP, GRAM, KILOGRAM),       CUP),
    MINOR_콩두부    ("콩/두부",  Level.MINOR, GRAM, List.of(GRAM, PIECE),               GRAM),
    MINOR_면류      ("면류",     Level.MINOR, GRAM, List.of(GRAM, PIECE),               GRAM),

    // 육류 (대분류 일괄도 가능하지만 사용자가 종류별 명시했으므로 그대로)
    MINOR_소고기    ("소고기",   Level.MINOR, GRAM, List.of(GRAM, KILOGRAM),            GRAM),
    MINOR_돼지고기  ("돼지고기", Level.MINOR, GRAM, List.of(GRAM, KILOGRAM),            GRAM),
    MINOR_닭고기    ("닭고기",   Level.MINOR, GRAM, List.of(GRAM, KILOGRAM),            GRAM),
    MINOR_오리고기  ("오리고기", Level.MINOR, GRAM, List.of(GRAM, KILOGRAM),            GRAM),
    MINOR_양고기    ("양고기",   Level.MINOR, GRAM, List.of(GRAM, KILOGRAM),            GRAM),
    MINOR_계란      ("계란",     Level.MINOR, EGG,  List.of(EGG, GRAM),                 EGG),
    MINOR_가공육    ("가공육",   Level.MINOR, GRAM, List.of(GRAM, PIECE),               GRAM),

    // 수산물
    MINOR_생선      ("생선",       Level.MINOR, GRAM, List.of(GRAM, FISH),              GRAM),
    MINOR_조개전복  ("조개/전복",  Level.MINOR, GRAM, List.of(GRAM, FISH),              GRAM),
    MINOR_새우게    ("새우/게",    Level.MINOR, GRAM, List.of(GRAM, FISH),              GRAM),
    MINOR_멸치건어물("멸치/건어물", Level.MINOR, GRAM, List.of(GRAM),                   GRAM),
    MINOR_오징어문어("오징어/문어", Level.MINOR, GRAM, List.of(GRAM, FISH),             GRAM),
    MINOR_미역다시마("미역/다시마", Level.MINOR, GRAM, List.of(GRAM),                   GRAM),
    MINOR_어육가공  ("어육가공식품", Level.MINOR, GRAM, List.of(GRAM),                  GRAM),
    MINOR_어란      ("어란",      Level.MINOR, GRAM, List.of(GRAM),                    GRAM),

    // 과일·견과류
    MINOR_과일      ("과일",     Level.MINOR, GRAM, List.of(GRAM, PIECE),               PIECE),
    MINOR_견과류    ("견과류",   Level.MINOR, GRAM, List.of(GRAM),                      GRAM),
    MINOR_냉동과일  ("냉동과일", Level.MINOR, GRAM, List.of(GRAM),                      GRAM),

    // 즉석·가공 식품
    MINOR_밀키트    ("밀키트",   Level.MINOR, PIECE, List.of(PIECE),                    PIECE),
    MINOR_레트로트  ("레트로트", Level.MINOR, PIECE, List.of(PIECE),                    PIECE),
    MINOR_즉석밥    ("즉석밥",   Level.MINOR, PIECE, List.of(PIECE),                    PIECE),
    MINOR_라면      ("라면",     Level.MINOR, PIECE, List.of(PIECE),                    PIECE),
    MINOR_냉동식품  ("냉동식품", Level.MINOR, GRAM,  List.of(GRAM, PIECE),               PIECE),
    MINOR_샐러드팩  ("샐러드팩", Level.MINOR, GRAM,  List.of(GRAM, PIECE),               PIECE),
    MINOR_반찬류    ("반찬류",   Level.MINOR, GRAM,  List.of(GRAM, PIECE),               GRAM),

    // 양념·소스·향신료
    MINOR_식용유    ("식용유",     Level.MINOR, MILLILITER, List.of(MILLILITER, CUP, TABLESPOON),                 TABLESPOON),
    MINOR_양념소스  ("양념/소스", Level.MINOR, MILLILITER, List.of(MILLILITER, CUP, TABLESPOON, TEASPOON),        TABLESPOON),
    MINOR_조미료    ("조미료",     Level.MINOR, GRAM,       List.of(GRAM, TABLESPOON, TEASPOON),                  TABLESPOON),
    MINOR_가루분말  ("가루분말",   Level.MINOR, GRAM,       List.of(GRAM, TABLESPOON, TEASPOON),                  TABLESPOON),
    MINOR_향신료허브("향신료/허브", Level.MINOR, GRAM,      List.of(GRAM),                                         GRAM),

    // 유제품
    MINOR_우유두유    ("우유/두유",   Level.MINOR, MILLILITER, List.of(MILLILITER, CUP, LITER),    MILLILITER),
    MINOR_요거트      ("요거트",     Level.MINOR, MILLILITER, List.of(MILLILITER, PIECE),         PIECE),
    MINOR_치즈        ("치즈",       Level.MINOR, GRAM,       List.of(GRAM, CHEESE_SLICE),        GRAM),
    MINOR_버터생크림  ("버터/생크림", Level.MINOR, GRAM,      List.of(GRAM, TABLESPOON),          GRAM),

    // 기타
    MINOR_참치스팸캔  ("참치/스팸캔", Level.MINOR, CAN,  List.of(CAN, GRAM),                CAN),
    MINOR_김자반      ("김/자반",    Level.MINOR, GRAM, List.of(GRAM),                     GRAM),
    MINOR_잼스프레드  ("잼/스프레드", Level.MINOR, GRAM, List.of(GRAM, TABLESPOON),         TABLESPOON),
    MINOR_시리얼      ("시리얼",     Level.MINOR, GRAM, List.of(GRAM, BOWL),               BOWL);

    private final String categoryName;
    private final Level level;
    private final REFUsageUnit defaultUnit;
    private final List<REFUsageUnit> allowedUsageUnits;
    private final REFUsageUnit defaultUsageUnit;

    REFGroceryItemUsageProfile(String categoryName,
                               Level level,
                               REFUsageUnit defaultUnit,
                               List<REFUsageUnit> allowedUsageUnits,
                               REFUsageUnit defaultUsageUnit) {
        this.categoryName = categoryName;
        this.level = level;
        this.defaultUnit = defaultUnit;
        this.allowedUsageUnits = List.copyOf(allowedUsageUnits);
        this.defaultUsageUnit = defaultUsageUnit;

        if (defaultUnit != null && !defaultUnit.isBaseUnit()) {
            throw new IllegalStateException(
                    "프로필 " + this.name() + "의 defaultUnit이 BASE가 아닙니다: " + defaultUnit);
        }
        if (!allowedUsageUnits.contains(defaultUsageUnit)) {
            throw new IllegalStateException(
                    "프로필 " + this.name() + "의 defaultUsageUnit이 allowedUsageUnits에 포함되지 않습니다.");
        }
    }

    /**
     * 중분류 이름으로 프로필을 검색합니다 (MINOR 레벨에 한해).
     */
    public static Optional<REFGroceryItemUsageProfile> findByMinorCategoryName(String minorName) {
        if (minorName == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(p -> p.level == Level.MINOR && p.categoryName.equals(minorName))
                .findFirst();
    }

    /**
     * 대분류 이름으로 프로필을 검색합니다 (MAJOR 레벨에 한해).
     */
    public static Optional<REFGroceryItemUsageProfile> findByMajorCategoryName(String majorName) {
        if (majorName == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(p -> p.level == Level.MAJOR && p.categoryName.equals(majorName))
                .findFirst();
    }

    /**
     * 중분류 → 대분류 fallback 검색.
     */
    public static Optional<REFGroceryItemUsageProfile> findByCategoryNames(
            String minorName, String majorName) {
        Optional<REFGroceryItemUsageProfile> minor = findByMinorCategoryName(minorName);
        if (minor.isPresent()) return minor;
        return findByMajorCategoryName(majorName);
    }

    /**
     * 이 프로필 값으로 채워진 메타데이터 VO를 빌드합니다.
     * pieceWeightGram, portion 범위는 카테고리만으로 결정 불가 → null로 둡니다.
     */
    public REFGroceryItemUsageMetadata toMetadata() {
        return REFGroceryItemUsageMetadata.of(
                this.defaultUnit,
                null,                          // minPortionAmount — Layer 2/3에서 채움
                null,                          // maxPortionAmount — Layer 2/3에서 채움
                null,                          // pieceWeightGram  — Layer 2/3에서 채움 (TODO)
                this.allowedUsageUnits,
                this.defaultUsageUnit
        );
    }

    public enum Level {
        MAJOR, MINOR
    }
}