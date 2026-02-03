package com.refridge.core_server.groceryItem.domain

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem
import com.refridge.core_server.groceryItem.domain.vo.*
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class REFGroceryItemSpec extends Specification {

    @Subject
    REFGroceryItem groceryItem

    def setup() {
        groceryItem = new REFGroceryItem(
                "양파",
                "https://example.com/onion.jpg",
                "F"
        )
    }

    // ============================================
    // 생성자 테스트
    // ============================================

    def "식료품을 생성할 수 있다"() {
        when: "식료품을 생성하면"
        def item = new REFGroceryItem(
                "토마토",
                "https://example.com/tomato.jpg",
                "F"
        )

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
    def "다양한 분류로 식료품을 생성할 수 있다: #itemName (#classificationType)"() {
        when: "특정 분류로 생성하면"
        def item = new REFGroceryItem(itemName, imageUrl, classificationType)

        then: "올바른 분류가 설정된다"
        item.groceryItemClassification == expectedClassification

        where:
        itemName   | imageUrl                        | classificationType || expectedClassification
        "소고기"    | "https://example.com/beef.jpg"   | "F"               || REFGroceryItemClassification.FOOD_INGREDIENTS
        "즉석밥"    | "https://example.com/rice.jpg"   | "R"               || REFGroceryItemClassification.RETORT_POUCH
        "샐러드키트" | "https://example.com/salad.jpg"  | "M"               || REFGroceryItemClassification.MEAL_KIT
    }

    def "잘못된 분류 코드로 생성 시 예외가 발생한다"() {
        when: "잘못된 분류 코드로 생성하면"
        new REFGroceryItem("상품", "url", "INVALID_CODE")

        then: "RuntimeException이 발생한다"
        thrown(RuntimeException)
    }

    // ============================================
    // changeRepresentativeImage 테스트
    // ============================================

    def "대표 이미지를 변경할 수 있다"() {
        given: "새로운 이미지 URL"
        def newImageUrl = "https://example.com/new-onion.jpg"
        def originalImage = groceryItem.representativeImage

        when: "이미지를 변경하면"
        def result = groceryItem.changeRepresentativeImage(newImageUrl)

        then: "새로운 이미지 객체가 반환된다"
        result != null
        result != originalImage

        and: "내부 이미지가 변경된다"
        groceryItem.representativeImage == result
    }

    def "대표 이미지 변경 시 반환값을 받을 수 있다"() {
        given: "새로운 이미지 URL"
        def newImageUrl = "https://example.com/changed.jpg"

        when: "이미지를 변경하면"
        def returnedImage = groceryItem.changeRepresentativeImage(newImageUrl)

        then: "변경된 이미지 객체가 반환된다"
        returnedImage instanceof REFRepresentativeImage
    }

    def "동일한 URL로 이미지를 변경해도 예외가 발생하지 않는다"() {
        given: "현재와 동일한 URL"
        def sameUrl = "https://example.com/onion.jpg"

        when: "동일한 URL로 변경하면"
        groceryItem.changeRepresentativeImage(sameUrl)

        then: "예외가 발생하지 않는다"
        noExceptionThrown()
    }

    def "삭제된 상태에서도 이미지를 변경할 수 있다"() {
        given: "삭제된 식료품"
        groceryItem.delete()

        when: "이미지를 변경하면"
        def result = groceryItem.changeRepresentativeImage("new-url")

        then: "정상적으로 변경된다"
        result != null
        noExceptionThrown()
    }

    // ============================================
    // addMatchedProduct 테스트
    // ============================================

    def "활성 상태일 때 매칭 상품을 추가할 수 있다"() {
        given: "활성 상태의 식료품"
        assert groceryItem.groceryItemStatus == REFGroceryItemStatus.ACTIVE

        and: "상품명"
        def productName = REFRealProductName.of("농협 양파 1kg")

        when: "상품을 추가하면"
        groceryItem.addMatchedProduct(productName)

        then: "Set에 추가된다"
        groceryItem.realProductNameSet.size() == 1
        groceryItem.realProductNameSet.contains(productName)
    }

    def "삭제된 상태일 때는 매칭 상품을 추가할 수 없다"() {
        given: "삭제된 식료품"
        groceryItem.delete()
        assert groceryItem.groceryItemStatus == REFGroceryItemStatus.DELETED

        and: "상품명"
        def productName = REFRealProductName.of("농협 양파 1kg")

        when: "상품을 추가하려고 하면"
        groceryItem.addMatchedProduct(productName)

        then: "추가되지 않는다"
        groceryItem.realProductNameSet.isEmpty()
    }

    def "여러 상품을 추가할 수 있다"() {
        given: "여러 상품명"
        def product1 = REFRealProductName.of("농협 양파 1kg")
        def product2 = REFRealProductName.of("신선한 양파 2kg")
        def product3 = REFRealProductName.of("유기농 양파")

        when: "상품들을 추가하면"
        groceryItem.addMatchedProduct(product1)
        groceryItem.addMatchedProduct(product2)
        groceryItem.addMatchedProduct(product3)

        then: "모두 추가된다"
        groceryItem.realProductNameSet.size() == 3
        groceryItem.realProductNameSet.containsAll([product1, product2, product3])
    }

    def "중복된 상품은 한 번만 추가된다"() {
        given: "동일한 상품명"
        def productName = REFRealProductName.of("농협 양파 1kg")

        when: "동일한 상품을 여러 번 추가하면"
        groceryItem.addMatchedProduct(productName)
        groceryItem.addMatchedProduct(productName)
        groceryItem.addMatchedProduct(productName)

        then: "하나만 저장된다 (Set 특성)"
        groceryItem.realProductNameSet.size() == 1
    }

    // ============================================
    // removeMatchedProduct 테스트
    // ============================================

    def "활성 상태일 때 매칭 상품을 삭제할 수 있다"() {
        given: "활성 상태이고 상품이 추가된 식료품"
        def productName = REFRealProductName.of("농협 양파 1kg")
        groceryItem.addMatchedProduct(productName)
        assert groceryItem.groceryItemStatus == REFGroceryItemStatus.ACTIVE

        when: "상품을 삭제하면"
        groceryItem.removeMatchedProduct(productName)

        then: "Set에서 제거된다"
        groceryItem.realProductNameSet.isEmpty()
        !groceryItem.realProductNameSet.contains(productName)
    }

    def "삭제된 상태일 때는 매칭 상품을 삭제할 수 없다"() {
        given: "상품이 추가된 식료품"
        def productName = REFRealProductName.of("농협 양파 1kg")
        groceryItem.addMatchedProduct(productName)

        and: "식료품을 삭제 상태로 변경"
        groceryItem.delete()
        assert groceryItem.groceryItemStatus == REFGroceryItemStatus.DELETED

        when: "상품 삭제를 시도하면"
        groceryItem.removeMatchedProduct(productName)

        then: "상품이 삭제되지 않는다"
        groceryItem.realProductNameSet.size() == 1
        groceryItem.realProductNameSet.contains(productName)
    }

    def "여러 상품 중 특정 상품만 삭제할 수 있다"() {
        given: "여러 상품 추가"
        def product1 = REFRealProductName.of("상품1")
        def product2 = REFRealProductName.of("상품2")
        def product3 = REFRealProductName.of("상품3")
        groceryItem.addMatchedProduct(product1)
        groceryItem.addMatchedProduct(product2)
        groceryItem.addMatchedProduct(product3)

        when: "하나만 삭제하면"
        groceryItem.removeMatchedProduct(product2)

        then: "해당 상품만 제거된다"
        groceryItem.realProductNameSet.size() == 2
        !groceryItem.realProductNameSet.contains(product2)
        groceryItem.realProductNameSet.containsAll([product1, product3])
    }

    def "존재하지 않는 상품을 삭제해도 예외가 발생하지 않는다"() {
        given: "존재하지 않는 상품"
        def nonExistentProduct = REFRealProductName.of("없는 상품")

        when: "삭제를 시도하면"
        groceryItem.removeMatchedProduct(nonExistentProduct)

        then: "예외가 발생하지 않는다"
        noExceptionThrown()
    }

    def "빈 Set에서 상품을 삭제해도 예외가 발생하지 않는다"() {
        given: "빈 realProductNameSet"
        assert groceryItem.realProductNameSet.isEmpty()

        when: "삭제를 시도하면"
        groceryItem.removeMatchedProduct(REFRealProductName.of("상품"))

        then: "예외가 발생하지 않는다"
        noExceptionThrown()
    }

    // ============================================
    // compareToProductAndGetGroceryItemDetailsForFridgeStock 테스트
    // ============================================

    def "매칭된 상품으로 냉장고 재고 정보를 획득할 수 있다"() {
        given: "매칭된 상품"
        def productName = "농협 양파 1kg"
        groceryItem.addMatchedProduct(REFRealProductName.of(productName))

        when: "상품명으로 조회하면"
        def result = groceryItem.compareToProductAndGetGroceryItemDetailsForFridgeStock(productName)

        then: "Optional에 값이 존재한다"
        result.isPresent()

        and: "올바른 정보가 담겨있다"
        def details = result.get()
        details != null
        details.matchedProductName == productName
    }

    def "매칭되지 않은 상품으로 조회 시 빈 Optional을 반환한다"() {
        given: "매칭되지 않은 상품명"
        def unmatchedProductName = "토마토 1kg"

        when: "조회하면"
        def result = groceryItem.compareToProductAndGetGroceryItemDetailsForFridgeStock(unmatchedProductName)

        then: "빈 Optional이 반환된다"
        result.isEmpty()
    }

    def "매칭 상품이 없을 때 조회 시 빈 Optional을 반환한다"() {
        given: "매칭 상품이 없는 상태"
        assert groceryItem.realProductNameSet.isEmpty()

        when: "조회하면"
        def result = groceryItem.compareToProductAndGetGroceryItemDetailsForFridgeStock("아무 상품")

        then: "빈 Optional이 반환된다"
        result.isEmpty()
    }

    @Unroll
    def "여러 매칭 상품 중 '#productName'으로 조회 시 결과: #shouldMatch"() {
        given: "여러 상품 등록"
        matchedProducts.each {
            groceryItem.addMatchedProduct(REFRealProductName.of(it))
        }

        when: "특정 상품으로 조회하면"
        def result = groceryItem.compareToProductAndGetGroceryItemDetailsForFridgeStock(productName)

        then: "예상된 결과를 반환한다"
        result.isPresent() == shouldMatch

        where:
        matchedProducts                      | productName      || shouldMatch
        ["농협 양파 1kg", "신선한 양파"]          | "농협 양파 1kg"     || true
        ["농협 양파 1kg", "신선한 양파"]          | "신선한 양파"       || true
        ["농협 양파 1kg", "신선한 양파"]          | "토마토"          || false
        ["상품A", "상품B", "상품C"]             | "상품B"          || true
        ["상품A"]                           | "상품B"          || false
    }

    def "null 상품명으로 조회 시 빈 Optional을 반환한다"() {
        when: "null로 조회하면"
        def result = groceryItem.compareToProductAndGetGroceryItemDetailsForFridgeStock(null)

        then: "빈 Optional이 반환된다"
        result.isEmpty()
    }

    def "삭제된 상태에서도 조회는 가능하다"() {
        given: "상품이 등록된 후 삭제된 식료품"
        def productName = "농협 양파 1kg"
        groceryItem.addMatchedProduct(REFRealProductName.of(productName))
        groceryItem.delete()

        when: "조회하면"
        def result = groceryItem.compareToProductAndGetGroceryItemDetailsForFridgeStock(productName)

        then: "조회가 가능하다"
        result.isPresent()
    }

    // ============================================
    // delete 테스트
    // ============================================

    def "식료품을 삭제 상태로 변경할 수 있다"() {
        given: "활성 상태의 식료품"
        assert groceryItem.groceryItemStatus == REFGroceryItemStatus.ACTIVE

        when: "삭제하면"
        groceryItem.delete()

        then: "상태가 DELETED로 변경된다"
        groceryItem.groceryItemStatus == REFGroceryItemStatus.DELETED
    }

    def "삭제된 식료품을 다시 삭제해도 예외가 발생하지 않는다"() {
        given: "이미 삭제된 식료품"
        groceryItem.delete()
        assert groceryItem.groceryItemStatus == REFGroceryItemStatus.DELETED

        when: "다시 삭제하면"
        groceryItem.delete()

        then: "예외가 발생하지 않는다"
        noExceptionThrown()

        and: "여전히 DELETED 상태다"
        groceryItem.groceryItemStatus == REFGroceryItemStatus.DELETED
    }

    def "삭제 후에도 다른 속성은 유지된다"() {
        given: "상품이 등록된 식료품"
        groceryItem.addMatchedProduct(REFRealProductName.of("상품1"))
        def originalProductCount = groceryItem.realProductNameSet.size()

        when: "삭제하면"
        groceryItem.delete()

        then: "다른 속성은 그대로 유지된다"
        groceryItem.realProductNameSet.size() == originalProductCount
        groceryItem.groceryItemName != null
        groceryItem.representativeImage != null
        groceryItem.groceryItemClassification != null
    }

    // ============================================
    // restore 테스트
    // ============================================

    def "삭제된 식료품을 복구할 수 있다"() {
        given: "삭제된 식료품"
        groceryItem.delete()
        assert groceryItem.groceryItemStatus == REFGroceryItemStatus.DELETED

        when: "복구하면"
        groceryItem.restore()

        then: "상태가 ACTIVE로 변경된다"
        groceryItem.groceryItemStatus == REFGroceryItemStatus.ACTIVE
    }

    def "활성 상태의 식료품을 복구해도 예외가 발생하지 않는다"() {
        given: "활성 상태의 식료품"
        assert groceryItem.groceryItemStatus == REFGroceryItemStatus.ACTIVE

        when: "복구를 시도하면"
        groceryItem.restore()

        then: "예외가 발생하지 않는다"
        noExceptionThrown()

        and: "여전히 ACTIVE 상태다"
        groceryItem.groceryItemStatus == REFGroceryItemStatus.ACTIVE
    }

    def "복구 후 상품을 추가할 수 있다"() {
        given: "삭제 후 복구된 식료품"
        groceryItem.delete()
        groceryItem.restore()

        when: "상품을 추가하면"
        def productName = REFRealProductName.of("신규 상품")
        groceryItem.addMatchedProduct(productName)

        then: "정상적으로 추가된다"
        groceryItem.realProductNameSet.size() == 1
        groceryItem.realProductNameSet.contains(productName)
    }

    def "복구 후 기존 상품은 유지된다"() {
        given: "상품이 등록된 식료품"
        def productName = REFRealProductName.of("기존 상품")
        groceryItem.addMatchedProduct(productName)
        def originalCount = groceryItem.realProductNameSet.size()

        when: "삭제 후 복구하면"
        groceryItem.delete()
        groceryItem.restore()

        then: "기존 상품이 그대로 유지된다"
        groceryItem.realProductNameSet.size() == originalCount
        groceryItem.realProductNameSet.contains(productName)
    }

    // ============================================
    // 통합 시나리오 테스트
    // ============================================

    def "식료품 생성 후 상품 추가, 조회, 삭제 시나리오"() {
        given: "새로운 식료품"
        def item = new REFGroceryItem("감자", "url", "F")

        when: "상품을 추가하고"
        item.addMatchedProduct(REFRealProductName.of("농협 감자"))
        item.addMatchedProduct(REFRealProductName.of("햇감자"))

        then: "추가된 상품으로 조회할 수 있다"
        item.compareToProductAndGetGroceryItemDetailsForFridgeStock("농협 감자").isPresent()
        item.compareToProductAndGetGroceryItemDetailsForFridgeStock("햇감자").isPresent()

        when: "하나를 삭제하면"
        item.removeMatchedProduct(REFRealProductName.of("농협 감자"))

        then: "삭제된 상품은 조회되지 않는다"
        item.compareToProductAndGetGroceryItemDetailsForFridgeStock("농협 감자").isEmpty()
        item.compareToProductAndGetGroceryItemDetailsForFridgeStock("햇감자").isPresent()
    }

    def "이미지 변경과 상품 관리를 함께 수행할 수 있다"() {
        when: "이미지를 변경하고"
        groceryItem.changeRepresentativeImage("new-url")

        and: "상품을 추가하면"
        groceryItem.addMatchedProduct(REFRealProductName.of("상품"))

        then: "모든 작업이 정상 수행된다"
        groceryItem.realProductNameSet.size() == 1
        noExceptionThrown()
    }

    def "삭제 후 복구하면 다시 상품을 관리할 수 있다"() {
        given: "상품이 등록된 식료품"
        groceryItem.addMatchedProduct(REFRealProductName.of("상품1"))

        when: "삭제 후 복구하면"
        groceryItem.delete()
        groceryItem.restore()

        and: "새 상품을 추가하면"
        groceryItem.addMatchedProduct(REFRealProductName.of("상품2"))

        then: "정상적으로 추가된다"
        groceryItem.realProductNameSet.size() == 2
    }

    def "삭제된 상태에서는 상품 추가/삭제가 불가능하다"() {
        given: "상품이 등록된 식료품"
        def existingProduct = REFRealProductName.of("기존 상품")
        groceryItem.addMatchedProduct(existingProduct)

        when: "삭제하면"
        groceryItem.delete()

        and: "상품 추가를 시도하면"
        groceryItem.addMatchedProduct(REFRealProductName.of("새 상품"))

        then: "추가되지 않는다"
        groceryItem.realProductNameSet.size() == 1

        when: "상품 삭제를 시도하면"
        groceryItem.removeMatchedProduct(existingProduct)

        then: "삭제되지 않는다"
        groceryItem.realProductNameSet.size() == 1
        groceryItem.realProductNameSet.contains(existingProduct)
    }

    def "완전한 라이프사이클: 생성 -> 상품 추가 -> 삭제 -> 복구 -> 상품 추가"() {
        given: "새 식료품"
        def item = new REFGroceryItem("당근", "url", "F")

        when: "상품을 추가하고"
        item.addMatchedProduct(REFRealProductName.of("상품1"))

        then: "추가된다"
        item.realProductNameSet.size() == 1

        when: "삭제하고"
        item.delete()

        and: "추가를 시도하면"
        item.addMatchedProduct(REFRealProductName.of("상품2"))

        then: "추가되지 않는다"
        item.realProductNameSet.size() == 1

        when: "복구하고"
        item.restore()

        and: "다시 추가하면"
        item.addMatchedProduct(REFRealProductName.of("상품3"))

        then: "정상 추가된다"
        item.realProductNameSet.size() == 2
    }
}