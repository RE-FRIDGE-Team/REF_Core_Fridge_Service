package com.refridge.core_server.grocery_category.fixture

import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository
import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory

/**
 * REFMajorGroceryCategory Object Mother
 * 테스트에서 자주 사용되는 대분류 카테고리 객체를 생성하는 팩토리 클래스
 */
class REFMajorGroceryCategoryMother {

    /* 기본 과일 대분류 카테고리 (저장되지 않음) */
    static REFMajorGroceryCategory fruit() {
        return REFMajorGroceryCategory.create("과일")
    }

    /* 기본 채소 대분류 카테고리 (저장되지 않음) */
    static REFMajorGroceryCategory vegetable() {
        return REFMajorGroceryCategory.create("채소")
    }

    /* 기본 육류 대분류 카테고리 (저장되지 않음) */
    static REFMajorGroceryCategory meat() {
        return REFMajorGroceryCategory.create("육류")
    }

    /* 기본 수산물 대분류 카테고리 (저장되지 않음) */
    static REFMajorGroceryCategory seafood() {
        return REFMajorGroceryCategory.create("수산물")
    }

    /* 기본 유제품 대분류 카테고리 (저장되지 않음) */
    static REFMajorGroceryCategory dairy() {
        return REFMajorGroceryCategory.create("유제품")
    }

    /* 기본 곡물 대분류 카테고리 (저장되지 않음) */
    static REFMajorGroceryCategory grain() {
        return REFMajorGroceryCategory.create("곡물")
    }

    /* 기본 음료 대분류 카테고리 (저장되지 않음) */
    static REFMajorGroceryCategory beverage() {
        return REFMajorGroceryCategory.create("음료")
    }

    /* 저장된 과일 대분류 카테고리 */
    static REFMajorGroceryCategory persistedFruit(REFMajorGroceryCategoryRepository repository) {
        return repository.save(fruit())
    }

    /* 저장된 채소 대분류 카테고리 */
    static REFMajorGroceryCategory persistedVegetable(REFMajorGroceryCategoryRepository repository) {
        return repository.save(vegetable())
    }

    /* 저장된 육류 대분류 카테고리 */
    static REFMajorGroceryCategory persistedMeat(REFMajorGroceryCategoryRepository repository) {
        return repository.save(meat())
    }

    /* 커스텀 이름의 대분류 카테고리 (저장되지 않음) */
    static REFMajorGroceryCategory withName(String categoryName) {
        return REFMajorGroceryCategory.create(categoryName)
    }

    /* 커스텀 이름의 저장된 대분류 카테고리 */
    static REFMajorGroceryCategory persistedWithName(
            String categoryName,
            REFMajorGroceryCategoryRepository repository
    ) {
        return repository.save(REFMajorGroceryCategory.create(categoryName))
    }

    /* 여러 대분류 카테고리를 한번에 저장 */
    static List<REFMajorGroceryCategory> persistedMultiple(
            REFMajorGroceryCategoryRepository repository,
            String... categoryNames
    ) {
        return categoryNames.collect {
            repository.save(REFMajorGroceryCategory.create(it))
        }
    }

    /* 기본 대분류 카테고리 세트 (과일, 채소, 육류) */
    static List<REFMajorGroceryCategory> defaultSet() {
        return [fruit(), vegetable(), meat()]
    }

    /* 저장된 기본 대분류 카테고리 세트 */
    static List<REFMajorGroceryCategory> persistedDefaultSet(
            REFMajorGroceryCategoryRepository repository
    ) {
        return repository.saveAll([fruit(), vegetable(), meat()])
    }

    /* 전체 식품 카테고리 세트 */
    static List<REFMajorGroceryCategory> fullSet() {
        return [fruit(), vegetable(), meat(), seafood(), dairy(), grain(), beverage()]
    }

    /* 저장된 전체 식품 카테고리 세트 */
    static List<REFMajorGroceryCategory> persistedFullSet(
            REFMajorGroceryCategoryRepository repository
    ) {
        return repository.saveAll(fullSet())
    }

    /* 중분류와 함께 저장된 대분류 (테스트용) */
    static REFMajorGroceryCategory fruitWithMinorCategories(
            REFMajorGroceryCategoryRepository repository
    ) {
        def major = repository.save(fruit())
        return major
    }

    /* 최대 길이의 이름을 가진 대분류 */
    static REFMajorGroceryCategory withMaxLengthName() {
        return REFMajorGroceryCategory.create("12345678901234567890") // 20자
    }

    /* 최소 길이의 이름을 가진 대분류 */
    static REFMajorGroceryCategory withMinLengthName() {
        return REFMajorGroceryCategory.create("A") // 1자
    }

    /* 공백이 포함된 이름을 가진 대분류 */
    static REFMajorGroceryCategory withSpacesInName() {
        return REFMajorGroceryCategory.create("냉동 식품")
    }

    /* 특수문자가 포함된 이름을 가진 대분류 */
    static REFMajorGroceryCategory withSpecialCharsInName() {
        return REFMajorGroceryCategory.create("간식&디저트")
    }
}