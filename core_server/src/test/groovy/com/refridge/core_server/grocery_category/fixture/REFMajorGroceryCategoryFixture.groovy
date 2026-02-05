package com.refridge.core_server.grocery_category.fixture

import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory

/**
 * REFMajorGroceryCategory 테스트를 위한 Fixture Trait
 * 테스트 클래스에 믹스인하여 사용
 */
trait REFMajorGroceryCategoryFixture {

    /* 기본 대분류 카테고리 생성 */
    REFMajorGroceryCategory createDefaultMajorCategory() {
        return REFMajorGroceryCategory.create("과일")
    }

    /* 특정 이름의 대분류 카테고리 생성 */
    REFMajorGroceryCategory createMajorCategoryWithName(String categoryName) {
        return REFMajorGroceryCategory.create(categoryName)
    }

    /* 여러 개의 대분류 카테고리 생성 */
    List<REFMajorGroceryCategory> createMultipleMajorCategories(String... categoryNames) {
        return categoryNames.collect { REFMajorGroceryCategory.create(it) }
    }

    /* 중분류가 포함된 대분류 카테고리 생성 (저장 필요) */
    REFMajorGroceryCategory createMajorCategoryWithMinorCategories(
            String majorName,
            List<String> minorNames
    ) {
        def major = REFMajorGroceryCategory.create(majorName)
        // Note: 실제로 중분류를 추가하려면 대분류가 저장되어야 함
        return major
    }

    /* 빈 문자열로 대분류 생성 시도 (예외 발생 예상) */
    void createMajorCategoryWithEmptyName() {
        REFMajorGroceryCategory.create("")
    }

    /* null로 대분류 생성 시도 (예외 발생 예상) */
    void createMajorCategoryWithNullName() {
        REFMajorGroceryCategory.create(null)
    }

    /* 공백만 있는 이름으로 대분류 생성 시도 (예외 발생 예상) */
    void createMajorCategoryWithWhitespaceName() {
        REFMajorGroceryCategory.create("   ")
    }

    /* 최대 길이의 대분류 카테고리 생성 */
    REFMajorGroceryCategory createMajorCategoryWithMaxLengthName() {
        return REFMajorGroceryCategory.create("A" * 20)
    }

    /* 최대 길이를 초과하는 대분류 카테고리 생성 시도 (예외 발생 예상) */
    void createMajorCategoryWithTooLongName() {
        REFMajorGroceryCategory.create("A" * 21)
    }

    /* 테스트용 대분류 카테고리 이름 목록 */
    List<String> getTestMajorCategoryNames() {
        return ["과일", "채소", "육류", "수산물", "유제품", "곡물", "음료"]
    }

    /* 한글 대분류 카테고리 생성 */
    REFMajorGroceryCategory createKoreanMajorCategory() {
        return REFMajorGroceryCategory.create("한국식품")
    }

    /* 영문 대분류 카테고리 생성 */
    REFMajorGroceryCategory createEnglishMajorCategory() {
        return REFMajorGroceryCategory.create("Fruits")
    }

    /* 숫자가 포함된 대분류 카테고리 생성 */
    REFMajorGroceryCategory createMajorCategoryWithNumbers() {
        return REFMajorGroceryCategory.create("음료123")
    }

    /* 특수문자가 포함된 대분류 카테고리 생성 */
    REFMajorGroceryCategory createMajorCategoryWithSpecialChars() {
        return REFMajorGroceryCategory.create("과일&채소")
    }
}