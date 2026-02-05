package com.refridge.core_server.grocery_category.domain

import com.refridge.core_server.config.REFBaseRepositorySpec
import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory
import com.refridge.core_server.grocery_category.fixture.REFMajorGroceryCategoryFixture
import com.refridge.core_server.grocery_category.fixture.REFMajorGroceryCategoryMother
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Subject


/**
 * REFMajorGroceryCategory 도메인 비즈니스 로직 테스트
 */
class REFMajorGroceryCategorySpec extends REFBaseRepositorySpec implements REFMajorGroceryCategoryFixture {

    @Autowired
    REFMajorGroceryCategoryRepository majorCategoryRepository

    @Autowired
    REFMinorGroceryCategoryRepository minorCategoryRepository

    @Subject
    REFMajorGroceryCategory majorCategory

    def "context loads"() {
        expect:
        majorCategoryRepository != null
        minorCategoryRepository != null
    }

    def "create - 유효한 카테고리명으로 대분류를 생성할 수 있다"() {
        when: "유효한 카테고리명으로 대분류를 생성하면"
        majorCategory = REFMajorGroceryCategory.create(categoryName)

        then: "대분류가 정상적으로 생성된다"
        majorCategory != null
        majorCategory.getMajorCategoryNameText() == expectedName

        where:
        categoryName | expectedName
        "과일"        | "과일"
        "채소"        | "채소"
        "Fruits"     | "Fruits"
        "음료123"     | "음료123"
    }

    def "create - 앞뒤 공백이 있는 카테고리명은 trim되어 생성된다"() {
        when: "앞뒤 공백이 있는 카테고리명으로 생성하면"
        majorCategory = REFMajorGroceryCategory.create("  과일  ")

        then: "공백이 제거된 이름으로 생성된다"
        majorCategory.getMajorCategoryNameText() == "과일"
    }

    def "create - 빈 문자열로 대분류를 생성하면 예외가 발생한다"() {
        when: "빈 문자열로 대분류를 생성하면"
        createMajorCategoryWithEmptyName()

        then: "예외가 발생한다"
        thrown(IllegalArgumentException)
    }

    def "create - null로 대분류를 생성하면 예외가 발생한다"() {
        when: "null로 대분류를 생성하면"
        createMajorCategoryWithNullName()

        then: "예외가 발생한다"
        thrown(IllegalArgumentException)
    }

    def "create - 공백만 있는 이름으로 대분류를 생성하면 예외가 발생한다"() {
        when: "공백만 있는 이름으로 대분류를 생성하면"
        createMajorCategoryWithWhitespaceName()

        then: "예외가 발생한다"
        thrown(IllegalArgumentException)
    }

    def "create - 최대 길이(20자)의 카테고리명으로 생성할 수 있다"() {
        when: "20자 길이의 카테고리명으로 생성하면"
        majorCategory = createMajorCategoryWithMaxLengthName()

        then: "정상적으로 생성된다"
        majorCategory != null
        majorCategory.getMajorCategoryNameText().length() == 20
    }

    def "create - 최대 길이를 초과하면 예외가 발생한다"() {
        when: "21자 이상의 카테고리명으로 생성하면"
        createMajorCategoryWithTooLongName()

        then: "예외가 발생한다"
        thrown(IllegalArgumentException)
    }

    def "createAndSave - 새로운 대분류를 생성하고 저장할 수 있다"() {
        when: "새로운 대분류를 생성하고 저장하면"
        majorCategory = REFMajorGroceryCategory.createAndSave("과일", majorCategoryRepository)

        then: "대분류가 저장되고 ID가 할당된다"
        majorCategory.id != null
        majorCategory.getMajorCategoryNameText() == "과일"

        and: "데이터베이스에서 조회할 수 있다"
        majorCategoryRepository.findById(majorCategory.id).isPresent()
    }

    def "createAndSave - 중복된 이름으로 대분류를 생성하면 예외가 발생한다"() {
        given: "이미 '과일' 대분류가 존재하는 상태에서"
        REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)

        when: "같은 이름으로 대분류를 생성하면"
        REFMajorGroceryCategory.createAndSave("과일", majorCategoryRepository)

        then: "예외가 발생한다"
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("이미 존재하는 대분류")
    }

    def "addMinorCategoryAndSaveViaMajorCategory - 대분류에 중분류를 추가하고 저장할 수 있다"() {
        given: "저장된 대분류가 존재하고"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)

        when: "중분류를 추가하고 저장하면"
        def minorCategory = majorCategory.addMinorCategoryAndSaveViaMajorCategory(
                "사과", minorCategoryRepository
        )

        then: "중분류가 저장되고 ID가 할당된다"
        minorCategory.id != null
        minorCategory.getMinorCategoryNameText() == "사과"

        and: "중분류가 대분류와 연결된다"
        majorCategory.hasMinorCategoryWithName("사과")
    }

    def "addMinorCategoryAndSaveViaMajorCategory - 중복된 중분류를 추가하면 예외가 발생한다"() {
        given: "중분류가 이미 추가된 대분류가 있을 때"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)
        majorCategory.addMinorCategoryAndSaveViaMajorCategory("사과", minorCategoryRepository)

        when: "같은 이름의 중분류를 다시 추가하면"
        majorCategory.addMinorCategoryAndSaveViaMajorCategory("사과", minorCategoryRepository)

        then: "예외가 발생한다"
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("이미 존재하는 중분류")
    }

    def "addMinorCategoriesAndSaveViaMajorCategory - 여러 중분류를 한번에 추가하고 저장할 수 있다"() {
        given: "저장된 대분류가 존재하고"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)

        when: "여러 중분류를 한번에 추가하면"
        def minorNames = ["사과", "배", "포도"]
        def minorCategories = majorCategory.addMinorCategoriesAndSaveViaMajorCategory(
                minorNames, minorCategoryRepository
        )

        then: "모든 중분류가 저장된다"
        minorCategories.size() == 3
        minorCategories.every { it.id != null }

        and: "대분류에서 중분류들을 찾을 수 있다"
        majorCategory.hasMinorCategoryWithName("사과")
        majorCategory.hasMinorCategoryWithName("배")
        majorCategory.hasMinorCategoryWithName("포도")
    }

    def "addMinorCategoriesAndSaveViaMajorCategory - 빈 리스트로 호출하면 빈 리스트를 반환한다"() {
        given: "저장된 대분류가 존재하고"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)

        when: "빈 리스트로 중분류를 추가하면"
        def result = majorCategory.addMinorCategoriesAndSaveViaMajorCategory(
                [], minorCategoryRepository
        )

        then: "빈 리스트가 반환된다"
        result.isEmpty()
    }

    def "hasMinorCategoryWithName - 중분류 이름으로 존재 여부를 확인할 수 있다"() {
        given: "중분류가 추가된 대분류가 있을 때"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)
        majorCategory.addMinorCategoryAndSaveViaMajorCategory("사과", minorCategoryRepository)

        expect: "존재하는 중분류는 true를 반환한다"
        majorCategory.hasMinorCategoryWithName("사과")

        and: "존재하지 않는 중분류는 false를 반환한다"
        !majorCategory.hasMinorCategoryWithName("배")
    }

    def "findMinorCategoryByName - 중분류 이름으로 중분류를 찾을 수 있다"() {
        given: "여러 중분류가 추가된 대분류가 있을 때"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)
        majorCategory.addMinorCategoriesAndSaveViaMajorCategory(
                ["사과", "배", "포도"], minorCategoryRepository
        )

        when: "중분류를 이름으로 찾으면"
        def foundMinor = majorCategory.findMinorCategoryByName("배")

        then: "해당 중분류를 찾을 수 있다"
        foundMinor.isPresent()
        foundMinor.get().getMinorCategoryNameText() == "배"
    }

    def "findMinorCategoryByName - 존재하지 않는 중분류를 찾으면 Empty를 반환한다"() {
        given: "대분류가 존재할 때"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)

        when: "존재하지 않는 중분류를 찾으면"
        def result = majorCategory.findMinorCategoryByName("존재하지않는중분류")

        then: "Empty가 반환된다"
        result.isEmpty()
    }

    def "removeMinorCategoryAndDelete - 중분류를 제거하고 삭제할 수 있다"() {
        given: "중분류가 추가된 대분류가 있을 때"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)
        def minorCategory = majorCategory.addMinorCategoryAndSaveViaMajorCategory(
                "사과", minorCategoryRepository
        )
        def minorId = minorCategory.id

        when: "중분류를 제거하고 삭제하면"
        majorCategory.removeMinorCategoryAndDelete(minorCategory, minorCategoryRepository)

        then: "대분류에서 중분류가 제거된다"
        !majorCategory.hasMinorCategoryWithName("사과")

        and: "데이터베이스에서도 삭제된다"
        !minorCategoryRepository.findById(minorId).isPresent()
    }

    def "removeMinorCategoryAndDelete - 대분류에 속하지 않은 중분류를 제거하면 예외가 발생한다"() {
        given: "두 개의 대분류가 존재하고"
        def fruitCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)
        def vegetableCategory = REFMajorGroceryCategoryMother.persistedVegetable(majorCategoryRepository)

        and: "채소 대분류에만 중분류가 추가되어 있을 때"
        def cabbage = vegetableCategory.addMinorCategoryAndSaveViaMajorCategory(
                "배추", minorCategoryRepository
        )

        when: "과일 대분류에서 배추를 제거하려고 하면"
        fruitCategory.removeMinorCategoryAndDelete(cabbage, minorCategoryRepository)

        then: "예외가 발생한다"
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("해당 중분류는 이 대분류에 속하지 않습니다")
    }

    def "removeMinorCategoryByNameAndDelete - 이름으로 중분류를 제거하고 삭제할 수 있다"() {
        given: "여러 중분류가 추가된 대분류가 있을 때"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)
        majorCategory.addMinorCategoriesAndSaveViaMajorCategory(
                ["사과", "배", "포도"], minorCategoryRepository
        )

        when: "이름으로 중분류를 제거하면"
        majorCategory.removeMinorCategoryByNameAndDelete("배", minorCategoryRepository)

        then: "해당 중분류가 제거된다"
        !majorCategory.hasMinorCategoryWithName("배")

        and: "다른 중분류는 그대로 유지된다"
        majorCategory.hasMinorCategoryWithName("사과")
        majorCategory.hasMinorCategoryWithName("포도")
    }

    def "removeMinorCategoryByNameAndDelete - 존재하지 않는 중분류를 제거하면 예외가 발생한다"() {
        given: "대분류가 존재할 때"
        majorCategory = REFMajorGroceryCategoryMother.persistedFruit(majorCategoryRepository)

        when: "존재하지 않는 중분류를 제거하려고 하면"
        majorCategory.removeMinorCategoryByNameAndDelete("존재하지않는중분류", minorCategoryRepository)

        then: "예외가 발생한다"
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("중분류를 찾을 수 없습니다")
    }

    def "대분류 생성 시 시간 메타데이터가 자동으로 설정된다"() {
        when: "대분류를 생성하고 저장하면"
        majorCategory = REFMajorGroceryCategory.createAndSave("과일", majorCategoryRepository)

        then: "생성 시간과 수정 시간이 설정된다"
        majorCategory.timeMetaData != null
    }

    def "여러 시나리오 - 대분류 생성, 중분류 추가, 중분류 제거의 전체 시나리오"() {
        given: "새로운 대분류를 생성하고"
        majorCategory = REFMajorGroceryCategory.createAndSave("과일", majorCategoryRepository)

        when: "여러 중분류를 추가하고"
        majorCategory.addMinorCategoriesAndSaveViaMajorCategory(
                ["사과", "배", "포도", "딸기"], minorCategoryRepository
        )

        then: "모든 중분류가 정상적으로 추가된다"
        majorCategory.hasMinorCategoryWithName("사과")
        majorCategory.hasMinorCategoryWithName("배")
        majorCategory.hasMinorCategoryWithName("포도")
        majorCategory.hasMinorCategoryWithName("딸기")

        when: "일부 중분류를 제거하고"
        majorCategory.removeMinorCategoryByNameAndDelete("배", minorCategoryRepository)
        majorCategory.removeMinorCategoryByNameAndDelete("딸기", minorCategoryRepository)

        then: "제거된 중분류는 찾을 수 없고"
        !majorCategory.hasMinorCategoryWithName("배")
        !majorCategory.hasMinorCategoryWithName("딸기")

        and: "남은 중분류는 정상적으로 존재한다"
        majorCategory.hasMinorCategoryWithName("사과")
        majorCategory.hasMinorCategoryWithName("포도")
    }
}