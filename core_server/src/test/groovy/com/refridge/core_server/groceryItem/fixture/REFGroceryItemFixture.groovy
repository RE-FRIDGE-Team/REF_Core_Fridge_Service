package com.refridge.core_server.groceryItem.fixture

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem
import com.refridge.core_server.groceryItem.domain.vo.REFRealProductName

/**
 * 테스트 클래스에 믹스인으로 사용할 Trait
 * 편의 메서드 제공
 */
trait REFGroceryItemFixture {
    // ============================================
    // 기본 생성 메서드
    // ============================================

    REFGroceryItem 기본_식료품() {
        REFGroceryItemMother.create()
    }

    REFGroceryItem 식료품(String name) {
        REFGroceryItemMother.create(name: name)
    }

    REFGroceryItem 식료품_생성(Map params) {
        REFGroceryItemMother.create(params)
    }

    // ============================================
    // 분류별 생성
    // ============================================

    REFGroceryItem 식재료(String name = "식재료") {
        REFGroceryItemMother.createFoodIngredient(name)
    }

    REFGroceryItem 레토르트(String name = "레토르트") {
        REFGroceryItemMother.createRetortPouch(name)
    }

    REFGroceryItem 밀키트(String name = "밀키트") {
        REFGroceryItemMother.createMealKit(name)
    }

    // ============================================
    // 상태별 생성
    // ============================================

    REFGroceryItem 삭제된_식료품(String name = "삭제된 상품") {
        REFGroceryItemMother.createDeleted(name)
    }

    REFGroceryItem 상품_등록된_식료품(String name, String... products) {
        REFGroceryItemMother.createWithProducts(name, products.toList())
    }

    // ============================================
    // 복합 시나리오
    // ============================================

    REFGroceryItem 완전한_식료품(String name, String imageUrl, List<String> products) {
        def item = REFGroceryItemMother.create(
                name: name,
                imageUrl: imageUrl
        )
        products.each { item.addMatchedProduct(REFRealProductName.of(it)) }
        return item
    }

    // ============================================
    // 영어 버전 (선호도에 따라 선택)
    // ============================================

    REFGroceryItem defaultItem() {
        REFGroceryItemMother.create()
    }

    REFGroceryItem item(String name) {
        REFGroceryItemMother.create(name: name)
    }

    REFGroceryItem itemWithProducts(String name, String... products) {
        REFGroceryItemMother.createWithProducts(name, products.toList())
    }

    REFGroceryItem deletedItem(String name = "deleted") {
        REFGroceryItemMother.createDeleted(name)
    }
}
