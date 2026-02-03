package com.refridge.core_server.groceryItem.domain

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem
import com.refridge.core_server.groceryItem.domain.vo.*
import com.refridge.core_server.groceryItem.fixture.REFGroceryItemFixture
import com.refridge.core_server.groceryItem.fixture.REFGroceryItemMother
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class REFGroceryItemSpec extends Specification implements REFGroceryItemFixture {
    @Subject
    REFGroceryItem groceryItem

    def setup() {
        groceryItem = 기본_식료품()
    }

    // ============================================
    // 생성자 테스트
    // ============================================

    def "식료품을 생성할 수 있다"() {
        when: "식료품을 생성하면"
        def item = 식료품("토마토")

        then: "예외가 발생하지 않는다"
        noExceptionThrown()
    }

    def "생성된 식료품은 활성 상태이다"() {
        expect: "초기 상태가 ACTIVE"
        groceryItem.groceryItemStatus == REFGroceryItemStatus.ACTIVE
    }

    def "생성 시 매칭된 상품이 없다"() {
        expect: "realProductNameSet이 비어있음"
        groceryItem.realProductNameSet.isEmpty()
    }

    @Unroll
    def "다양한 분류로 식료품을 생성할 수 있다: #분류"() {
        when: "특정 분류로 생성하면"
        def item = 생성메서드()

        then: "올바른 분류가 설정된다"
        item.groceryItemClassification == 예상분류

        where:
        분류      | 생성메서드                        || 예상분류
        "식재료"   | { 식재료("소고기") }              || REFGroceryItemClassification.FOOD_INGREDIENTS
        "레토르트" | { 레토르트("즉석밥") }             || REFGroceryItemClassification.RETORT_POUCH
        "밀키트"   | { 밀키트("샐러드키트") }           || REFGroceryItemClassification.MEAL_KIT
    }

    def "Builder 패턴으로 식료품을 생성할 수 있다"() {
        when: "Builder를 사용하면"
        def item = REFGroceryItemMother.builder
                .name("양파")
                .foodIngredient()
                .withProducts("농협 양파", "신선 양파")
                .build()

        then: "올바르게 생성된다"
        item.groceryItemClassification == REFGroceryItemClassification.FOOD_INGREDIENTS
        item.realProductNameSet.size() == 2
    }

    def "Map 파라미터로 유연하게 생성할 수 있다"() {
        when: "Map으로 생성하면"
        def item = 식료품_생성(
                name: "사과",
                classification: "F",
                products: ["사과 1박스", "유기농 사과"]
        )

        then: "정상 생성된다"
        item.realProductNameSet.size() == 2
    }

    def "잘못된 분류 코드로 생성 시 예외가 발생한다"() {
        when: "잘못된 분류 코드로 생성하면"
        REFGroceryItemMother.create(classification: "INVALID")

        then: "RuntimeException이 발생한다"
        thrown(RuntimeException)
    }

    // ============================================
    // changeRepresentativeImage 테스트
    // ============================================

    def "활성 상태에서 대표 이미지를 변경할 수 있다"() {
        given: "활성 상태의 식료품"
        def item = 식료품("양파")
        def 새이미지 = "https://example.com/new-onion.jpg"
        def 원본이미지 = item.representativeImage

        when: "이미지를 변경하면"
        def result = item.changeRepresentativeImage(새이미지)

        then: "새로운 이미지가 설정된다"
        result != null
        result != 원본이미지
        item.representativeImage == result
    }

    def "삭제된 상태에서는 이미지를 변경할 수 없다"() {
        given: "삭제된 식료품"
        def item = 삭제된_식료품()
        def 원본이미지 = item.representativeImage

        when: "이미지 변경을 시도하면"
        def result = item.changeRepresentativeImage("new-url")

        then: "이미지가 변경되지 않는다"
        item.representativeImage == 원본이미지
    }

    def "동일한 URL로 이미지를 변경해도 예외가 발생하지 않는다"() {
        given: "식료품"
        def item = 식료품("토마토")

        when: "동일한 URL로 변경하면"
        item.changeRepresentativeImage("https://example.com/default.jpg")

        then: "예외가 발생하지 않는다"
        noExceptionThrown()
    }

    // ============================================
    // addMatchedProduct 테스트
    // ============================================

    def "활성 상태일 때 매칭 상품을 추가할 수 있다"() {
        given: "활성 상태의 식료품"
        def item = 식료품("양파")
        def 상품명 = REFRealProductName.of("농협 양파 1kg")

        when: "상품을 추가하면"
        item.addMatchedProduct(상품명)

        then: "추가된다"
        item.realProductNameSet.size() == 1
        item.realProductNameSet.contains(상품명)
    }

    def "삭제된 상태일 때는 매칭 상품을 추가할 수 없다"() {
        given: "삭제된 식료품"
        def item = 삭제된_식료품()
        def 상품명 = REFRealProductName.of("농협 양파 1kg")

        when: "상품을 추가하려고 하면"
        item.addMatchedProduct(상품명)

        then: "추가되지 않는다"
        item.realProductNameSet.isEmpty()
    }

    def "여러 상품을 추가할 수 있다"() {
        given: "식료품"
        def item = 식료품("양파")

        when: "여러 상품을 추가하면"
        ["농협 양파 1kg", "신선한 양파 2kg", "유기농 양파"].each {
            item.addMatchedProduct(REFRealProductName.of(it))
        }

        then: "모두 추가된다"
        item.realProductNameSet.size() == 3
    }

    def "중복된 상품은 한 번만 추가된다"() {
        given: "식료품"
        def item = 식료품("양파")
        def 상품명 = REFRealProductName.of("농협 양파 1kg")

        when: "동일한 상품을 여러 번 추가하면"
        3.times { item.addMatchedProduct(상품명) }

        then: "하나만 저장된다"
        item.realProductNameSet.size() == 1
    }

    // ============================================
    // removeMatchedProduct 테스트
    // ============================================

    def "활성 상태일 때 매칭 상품을 삭제할 수 있다"() {
        given: "상품이 등록된 식료품"
        def item = 상품_등록된_식료품("양파", "농협 양파 1kg")
        def 상품명 = REFRealProductName.of("농협 양파 1kg")

        when: "상품을 삭제하면"
        item.removeMatchedProduct(상품명)

        then: "삭제된다"
        item.realProductNameSet.isEmpty()
    }

    def "삭제된 상태일 때는 매칭 상품을 삭제할 수 없다"() {
        given: "상품이 등록된 후 삭제된 식료품"
        def item = 상품_등록된_식료품("양파", "농협 양파 1kg")
        def 상품명 = REFRealProductName.of("농협 양파 1kg")
        item.delete()

        when: "상품 삭제를 시도하면"
        item.removeMatchedProduct(상품명)

        then: "삭제되지 않는다"
        item.realProductNameSet.size() == 1
    }

    def "여러 상품 중 특정 상품만 삭제할 수 있다"() {
        given: "여러 상품이 등록된 식료품"
        def item = 상품_등록된_식료품("양파", "상품1", "상품2", "상품3")
        def 삭제대상 = REFRealProductName.of("상품2")

        when: "하나만 삭제하면"
        item.removeMatchedProduct(삭제대상)

        then: "해당 상품만 제거된다"
        item.realProductNameSet.size() == 2
        !item.realProductNameSet.contains(삭제대상)
    }

    def "존재하지 않는 상품을 삭제해도 예외가 발생하지 않는다"() {
        given: "식료품"
        def item = 식료품("양파")

        when: "존재하지 않는 상품 삭제를 시도하면"
        item.removeMatchedProduct(REFRealProductName.of("없는 상품"))

        then: "예외가 발생하지 않는다"
        noExceptionThrown()
    }

    // ============================================
    // compareToProductAndGetGroceryItemDetailsForFridgeStock 테스트
    // ============================================

    def "매칭된 상품으로 냉장고 재고 정보를 획득할 수 있다"() {
        given: "상품이 등록된 식료품"
        def 상품명 = "농협 양파 1kg"
        def item = 상품_등록된_식료품("양파", 상품명)

        when: "상품명으로 조회하면"
        def result = item.compareToProductAndGetGroceryItemDetailsForFridgeStock(상품명)

        then: "올바른 정보가 반환된다"
        result.isPresent()
        result.get().matchedProductName == 상품명
    }

    def "매칭되지 않은 상품으로 조회 시 빈 Optional을 반환한다"() {
        given: "식료품"
        def item = 식료품("양파")

        when: "매칭되지 않은 상품으로 조회하면"
        def result = item.compareToProductAndGetGroceryItemDetailsForFridgeStock("토마토 1kg")

        then: "빈 Optional이 반환된다"
        result.isEmpty()
    }

    def "매칭 상품이 없을 때 조회 시 빈 Optional을 반환한다"() {
        given: "상품이 등록되지 않은 식료품"
        def item = 식료품("양파")

        when: "조회하면"
        def result = item.compareToProductAndGetGroceryItemDetailsForFridgeStock("아무 상품")

        then: "빈 Optional이 반환된다"
        result.isEmpty()
    }

    @Unroll
    def "여러 매칭 상품 중 '#상품명'으로 조회 시 결과: #매칭여부"() {
        given: "여러 상품이 등록된 식료품"
        def item = 상품_등록된_식료품("양파", *등록된상품들)

        when: "특정 상품으로 조회하면"
        def result = item.compareToProductAndGetGroceryItemDetailsForFridgeStock(상품명)

        then: "예상된 결과를 반환한다"
        result.isPresent() == 매칭여부

        where:
        등록된상품들                           | 상품명         || 매칭여부
        ["농협 양파 1kg", "신선한 양파"]        | "농협 양파 1kg"  || true
        ["농협 양파 1kg", "신선한 양파"]        | "신선한 양파"    || true
        ["농협 양파 1kg", "신선한 양파"]        | "토마토"        || false
        ["상품A", "상품B", "상품C"]           | "상품B"        || true
        ["상품A"]                          | "상품B"        || false
    }

    def "null 상품명으로 조회 시 빈 Optional을 반환한다"() {
        given: "식료품"
        def item = 식료품("양파")

        when: "null로 조회하면"
        def result = item.compareToProductAndGetGroceryItemDetailsForFridgeStock(null)

        then: "빈 Optional이 반환된다"
        result.isEmpty()
    }

    def "삭제된 상태에서도 조회는 가능하다"() {
        given: "상품이 등록된 후 삭제된 식료품"
        def 상품명 = "농협 양파 1kg"
        def item = 상품_등록된_식료품("양파", 상품명)
        item.delete()

        when: "조회하면"
        def result = item.compareToProductAndGetGroceryItemDetailsForFridgeStock(상품명)

        then: "조회가 가능하다"
        result.isPresent()
    }

    // ============================================
    // delete 테스트
    // ============================================

    def "식료품을 삭제 상태로 변경할 수 있다"() {
        given: "활성 상태의 식료품"
        def item = 식료품("양파")

        when: "삭제하면"
        item.delete()

        then: "삭제 상태가 된다"
        item.groceryItemStatus == REFGroceryItemStatus.DELETED
    }

    def "삭제된 식료품을 다시 삭제해도 예외가 발생하지 않는다"() {
        given: "삭제된 식료품"
        def item = 삭제된_식료품()

        when: "다시 삭제하면"
        item.delete()

        then: "예외가 발생하지 않는다"
        noExceptionThrown()
        item.groceryItemStatus == REFGroceryItemStatus.DELETED
    }

    def "삭제 후에도 다른 속성은 유지된다"() {
        given: "상품이 등록된 식료품"
        def item = 상품_등록된_식료품("양파", "상품1")
        def 원본상품개수 = item.realProductNameSet.size()

        when: "삭제하면"
        item.delete()

        then: "다른 속성은 유지된다"
        item.realProductNameSet.size() == 원본상품개수
        item.groceryItemName != null
        item.representativeImage != null
    }

    // ============================================
    // restore 테스트
    // ============================================

    def "삭제된 식료품을 복구할 수 있다"() {
        given: "삭제된 식료품"
        def item = 삭제된_식료품()

        when: "복구하면"
        item.restore()

        then: "활성 상태가 된다"
        item.groceryItemStatus == REFGroceryItemStatus.ACTIVE
    }

    def "활성 상태의 식료품을 복구해도 예외가 발생하지 않는다"() {
        given: "활성 상태의 식료품"
        def item = 식료품("양파")

        when: "복구를 시도하면"
        item.restore()

        then: "예외가 발생하지 않는다"
        noExceptionThrown()
        item.groceryItemStatus == REFGroceryItemStatus.ACTIVE
    }

    def "복구 후 상품을 추가할 수 있다"() {
        given: "삭제 후 복구된 식료품"
        def item = 삭제된_식료품()
        item.restore()

        when: "상품을 추가하면"
        def 상품명 = REFRealProductName.of("신규 상품")
        item.addMatchedProduct(상품명)

        then: "정상적으로 추가된다"
        item.realProductNameSet.size() == 1
    }

    def "복구 후 기존 상품은 유지된다"() {
        given: "상품이 등록된 식료품"
        def item = 상품_등록된_식료품("양파", "기존 상품")
        def 원본개수 = item.realProductNameSet.size()

        when: "삭제 후 복구하면"
        item.delete()
        item.restore()

        then: "기존 상품이 유지된다"
        item.realProductNameSet.size() == 원본개수
    }

    // ============================================
    // 통합 시나리오 테스트
    // ============================================

    def "식료품 생성 후 상품 추가, 조회, 삭제 시나리오"() {
        given: "새로운 식료품"
        def item = 식재료("감자")

        when: "상품을 추가하고"
        item.addMatchedProduct(REFRealProductName.of("농협 감자"))
        item.addMatchedProduct(REFRealProductName.of("햇감자"))

        then: "조회할 수 있다"
        item.compareToProductAndGetGroceryItemDetailsForFridgeStock("농협 감자").isPresent()
        item.compareToProductAndGetGroceryItemDetailsForFridgeStock("햇감자").isPresent()

        when: "하나를 삭제하면"
        item.removeMatchedProduct(REFRealProductName.of("농협 감자"))

        then: "삭제된 상품은 조회되지 않는다"
        item.compareToProductAndGetGroceryItemDetailsForFridgeStock("농협 감자").isEmpty()
        item.compareToProductAndGetGroceryItemDetailsForFridgeStock("햇감자").isPresent()
    }

    def "이미지 변경과 상품 관리를 함께 수행할 수 있다"() {
        given: "식료품"
        def item = 식료품("양파")

        when: "이미지를 변경하고"
        item.changeRepresentativeImage("new-url")

        and: "상품을 추가하면"
        item.addMatchedProduct(REFRealProductName.of("상품"))

        then: "모든 작업이 정상 수행된다"
        item.realProductNameSet.size() == 1
    }

    def "삭제 후 복구하면 다시 상품을 관리할 수 있다"() {
        given: "상품이 등록된 식료품"
        def item = 상품_등록된_식료품("양파", "상품1")

        when: "삭제 후 복구하면"
        item.delete()
        item.restore()

        and: "새 상품을 추가하면"
        item.addMatchedProduct(REFRealProductName.of("상품2"))

        then: "정상적으로 추가된다"
        item.realProductNameSet.size() == 2
    }

    def "삭제된 상태에서는 상품 추가/삭제가 불가능하다"() {
        given: "상품이 등록된 식료품"
        def 기존상품 = REFRealProductName.of("기존 상품")
        def item = 상품_등록된_식료품("양파", "기존 상품")

        when: "삭제하면"
        item.delete()

        and: "상품 추가를 시도하면"
        item.addMatchedProduct(REFRealProductName.of("새 상품"))

        then: "추가되지 않는다"
        item.realProductNameSet.size() == 1

        when: "상품 삭제를 시도하면"
        item.removeMatchedProduct(기존상품)

        then: "삭제되지 않는다"
        item.realProductNameSet.size() == 1
    }

    def "완전한 라이프사이클: 생성 -> 상품 추가 -> 삭제 -> 복구 -> 상품 추가"() {
        given: "새 식료품"
        def item = 식재료("당근")

        when: "상품을 추가하고"
        item.addMatchedProduct(REFRealProductName.of("상품1"))

        then: "추가된다"
        item.realProductNameSet.size() == 1

        when: "삭제하고 추가를 시도하면"
        item.delete()
        item.addMatchedProduct(REFRealProductName.of("상품2"))

        then: "추가되지 않는다"
        item.realProductNameSet.size() == 1

        when: "복구하고 다시 추가하면"
        item.restore()
        item.addMatchedProduct(REFRealProductName.of("상품3"))

        then: "정상 추가된다"
        item.realProductNameSet.size() == 2
    }

    def "Builder 패턴으로 복잡한 시나리오를 생성할 수 있다"() {
        given: "Builder로 생성한 식료품"
        def item = REFGroceryItemMother.builder()
                .name("양파")
                .imageUrl("https://example.com/onion.jpg")
                .foodIngredient()
                .withProducts("상품1", "상품2", "상품3")
                .build()

        expect: "모든 속성이 올바르게 설정된다"
        item.groceryItemClassification == REFGroceryItemClassification.FOOD_INGREDIENTS
        item.realProductNameSet.size() == 3
        item.groceryItemStatus == REFGroceryItemStatus.ACTIVE
    }
}