package com.refridge.core_server.grocery_category.fixture

import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory

/**
 * REFMinorGroceryCategory 테스트를 위한 Fixture Trait
 * 테스트 클래스에 믹스인하여 사용
 */
trait REFMinorGroceryCategoryFixture {

    /* 기본 중분류 카테고리 생성 (대분류 필요) */
    REFMinorGroceryCategory createDefaultMinorCategory(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("사과", majorCategory)
    }

    /* 특정 이름의 중분류 카테고리 생성 */
    REFMinorGroceryCategory createMinorCategoryWithName(
            String categoryName,
            REFMajorGroceryCategory majorCategory
    ) {
        return REFMinorGroceryCategory.create(categoryName, majorCategory)
    }

    /* 여러 개의 중분류 카테고리 생성 */
    List<REFMinorGroceryCategory> createMultipleMinorCategories(
            REFMajorGroceryCategory majorCategory,
            String... categoryNames
    ) {
        return categoryNames.collect {
            REFMinorGroceryCategory.create(it, majorCategory)
        }
    }

    /* null 대분류로 중분류 생성 시도 (예외 발생 예상) */
    void createMinorCategoryWithNullMajorCategory() {
        REFMinorGroceryCategory.create("사과", null)
    }

    /* 빈 문자열로 중분류 생성 시도 (예외 발생 예상) */
    void createMinorCategoryWithEmptyName(REFMajorGroceryCategory majorCategory) {
        REFMinorGroceryCategory.create("", majorCategory)
    }

    /* null로 중분류 생성 시도 (예외 발생 예상) */
    void createMinorCategoryWithNullName(REFMajorGroceryCategory majorCategory) {
        REFMinorGroceryCategory.create(null, majorCategory)
    }

    /* 공백만 있는 이름으로 중분류 생성 시도 (예외 발생 예상) */
    void createMinorCategoryWithWhitespaceName(REFMajorGroceryCategory majorCategory) {
        REFMinorGroceryCategory.create("   ", majorCategory)
    }

    /* 최대 길이의 중분류 카테고리 생성 */
    REFMinorGroceryCategory createMinorCategoryWithMaxLengthName(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("A" * 20, majorCategory)
    }

    /* 최대 길이를 초과하는 중분류 카테고리 생성 시도 (예외 발생 예상) */
    void createMinorCategoryWithTooLongName(REFMajorGroceryCategory majorCategory) {
        REFMinorGroceryCategory.create("A" * 21, majorCategory)
    }

    /* 과일 중분류 테스트 데이터 */
    List<String> getFruitMinorCategoryNames() {
        return ["사과", "배", "포도", "딸기", "수박", "참외", "복숭아"]
    }

    /* 채소 중분류 테스트 데이터 */
    List<String> getVegetableMinorCategoryNames() {
        return ["배추", "무", "당근", "양파", "감자", "고구마", "토마토"]
    }

    /* 육류 중분류 테스트 데이터 */
    List<String> getMeatMinorCategoryNames() {
        return ["소고기", "돼지고기", "닭고기", "오리고기", "양고기"]
    }

    /* 한글 중분류 카테고리 생성 */
    REFMinorGroceryCategory createKoreanMinorCategory(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("한국사과", majorCategory)
    }

    /* 영문 중분류 카테고리 생성 */
    REFMinorGroceryCategory createEnglishMinorCategory(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("Apple", majorCategory)
    }

    /* 숫자가 포함된 중분류 카테고리 생성 */
    REFMinorGroceryCategory createMinorCategoryWithNumbers(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("사과123", majorCategory)
    }

    /* 특수문자가 포함된 중분류 카테고리 생성 */
    REFMinorGroceryCategory createMinorCategoryWithSpecialChars(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("사과&배", majorCategory)
    }

    /* 공백이 포함된 중분류 카테고리 생성 */
    REFMinorGroceryCategory createMinorCategoryWithSpaces(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("신선 사과", majorCategory)
    }

    /* 최소 길이의 중분류 카테고리 생성 */
    REFMinorGroceryCategory createMinorCategoryWithMinLengthName(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("A", majorCategory)
    }

    /* 전체 경로 포맷팅 테스트용 중분류 생성 */
    REFMinorGroceryCategory createMinorForPathFormatTest(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("테스트중분류", majorCategory)
    }
}