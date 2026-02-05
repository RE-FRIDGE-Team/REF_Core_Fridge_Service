package com.refridge.core_server.grocery_category.fixture

import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository
import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory

/**
 * REFMinorGroceryCategory Object Mother
 * 테스트에서 자주 사용되는 중분류 카테고리 객체를 생성하는 팩토리 클래스
 */
class REFMinorGroceryCategoryMother {

    /* 과일 카테고리 - 사과 (저장되지 않음) */
    static REFMinorGroceryCategory apple(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("사과", majorCategory)
    }

    /* 과일 카테고리 - 배 (저장되지 않음) */
    static REFMinorGroceryCategory pear(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("배", majorCategory)
    }

    /* 과일 카테고리 - 포도 (저장되지 않음) */
    static REFMinorGroceryCategory grape(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("포도", majorCategory)
    }

    /* 과일 카테고리 - 딸기 (저장되지 않음) */
    static REFMinorGroceryCategory strawberry(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("딸기", majorCategory)
    }

    /* 채소 카테고리 - 배추 (저장되지 않음) */
    static REFMinorGroceryCategory cabbage(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("배추", majorCategory)
    }

    /* 채소 카테고리 - 무 (저장되지 않음) */
    static REFMinorGroceryCategory radish(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("무", majorCategory)
    }

    /* 채소 카테고리 - 당근 (저장되지 않음) */
    static REFMinorGroceryCategory carrot(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("당근", majorCategory)
    }

    /* 육류 카테고리 - 소고기 (저장되지 않음) */
    static REFMinorGroceryCategory beef(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("소고기", majorCategory)
    }

    /* 육류 카테고리 - 돼지고기 (저장되지 않음) */
    static REFMinorGroceryCategory pork(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("돼지고기", majorCategory)
    }

    /* 육류 카테고리 - 닭고기 (저장되지 않음) */
    static REFMinorGroceryCategory chicken(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("닭고기", majorCategory)
    }

    /* 저장된 사과 중분류 카테고리 */
    static REFMinorGroceryCategory persistedApple(
            REFMajorGroceryCategory majorCategory,
            REFMinorGroceryCategoryRepository repository
    ) {
        return repository.save(apple(majorCategory))
    }

    /* 저장된 배 중분류 카테고리 */
    static REFMinorGroceryCategory persistedPear(
            REFMajorGroceryCategory majorCategory,
            REFMinorGroceryCategoryRepository repository
    ) {
        return repository.save(pear(majorCategory))
    }

    /* 커스텀 이름의 중분류 카테고리 (저장되지 않음) */
    static REFMinorGroceryCategory withName(
            String categoryName,
            REFMajorGroceryCategory majorCategory
    ) {
        return REFMinorGroceryCategory.create(categoryName, majorCategory)
    }

    /* 커스텀 이름의 저장된 중분류 카테고리 */
    static REFMinorGroceryCategory persistedWithName(
            String categoryName,
            REFMajorGroceryCategory majorCategory,
            REFMinorGroceryCategoryRepository repository
    ) {
        return repository.save(REFMinorGroceryCategory.create(categoryName, majorCategory))
    }

    /* 여러 중분류 카테고리를 한번에 저장 */
    static List<REFMinorGroceryCategory> persistedMultiple(
            REFMajorGroceryCategory majorCategory,
            REFMinorGroceryCategoryRepository repository,
            String... categoryNames
    ) {
        return categoryNames.collect {
            repository.save(REFMinorGroceryCategory.create(it, majorCategory))
        }
    }

    /* 과일 기본 중분류 세트 (사과, 배, 포도) */
    static List<REFMinorGroceryCategory> fruitDefaultSet(REFMajorGroceryCategory majorCategory) {
        return [apple(majorCategory), pear(majorCategory), grape(majorCategory)]
    }

    /* 저장된 과일 기본 중분류 세트 */
    static List<REFMinorGroceryCategory> persistedFruitDefaultSet(
            REFMajorGroceryCategory majorCategory,
            REFMinorGroceryCategoryRepository repository
    ) {
        return repository.saveAll(fruitDefaultSet(majorCategory))
    }

    /* 과일 전체 중분류 세트 (사과, 배, 포도, 딸기) */
    static List<REFMinorGroceryCategory> fruitFullSet(REFMajorGroceryCategory majorCategory) {
        return [apple(majorCategory), pear(majorCategory), grape(majorCategory), strawberry(majorCategory)]
    }

    /* 채소 기본 중분류 세트 (배추, 무, 당근) */
    static List<REFMinorGroceryCategory> vegetableDefaultSet(REFMajorGroceryCategory majorCategory) {
        return [cabbage(majorCategory), radish(majorCategory), carrot(majorCategory)]
    }

    /* 육류 기본 중분류 세트 (소고기, 돼지고기, 닭고기) */
    static List<REFMinorGroceryCategory> meatDefaultSet(REFMajorGroceryCategory majorCategory) {
        return [beef(majorCategory), pork(majorCategory), chicken(majorCategory)]
    }

    /* 저장된 육류 기본 중분류 세트 */
    static List<REFMinorGroceryCategory> persistedMeatDefaultSet(
            REFMajorGroceryCategory majorCategory,
            REFMinorGroceryCategoryRepository repository
    ) {
        return repository.saveAll(meatDefaultSet(majorCategory))
    }

    /* 최대 길이의 이름을 가진 중분류 */
    static REFMinorGroceryCategory withMaxLengthName(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("12345678901234567890", majorCategory) // 20자
    }

    /* 최소 길이의 이름을 가진 중분류 */
    static REFMinorGroceryCategory withMinLengthName(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("A", majorCategory) // 1자
    }

    /* 공백이 포함된 이름을 가진 중분류 */
    static REFMinorGroceryCategory withSpacesInName(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("신선 사과", majorCategory)
    }

    /* 특수문자가 포함된 이름을 가진 중분류 */
    static REFMinorGroceryCategory withSpecialCharsInName(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("사과&배", majorCategory)
    }

    /* 경로 포맷 테스트용 중분류 */
    static REFMinorGroceryCategory forPathFormatTest(REFMajorGroceryCategory majorCategory) {
        return REFMinorGroceryCategory.create("테스트중분류", majorCategory)
    }
}