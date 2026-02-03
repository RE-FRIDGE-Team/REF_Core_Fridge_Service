package com.refridge.core_server.groceryItem.fixture

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem
import com.refridge.core_server.groceryItem.domain.vo.REFRealProductName


/**
 * GroceryItem 테스트 객체 생성 전담 클래스
 * 필드 변경 시 여기만 수정하면 모든 테스트에 자동 반영
 */
class REFGroceryItemMother {
    /**
     * 기본 GroceryItem 생성
     * 모든 필드에 기본값 제공
     */
    static REFGroceryItem create(Map params = [:]) {
        def item = new REFGroceryItem(
                params.name ?: "기본 상품",
                params.imageUrl ?: "https://example.com/default.jpg",
                params.classification ?: "F"
        )

        // 상품 등록
        if (params.products) {
            params.products.each { productName ->
                item.addMatchedProduct(REFRealProductName.of(productName))
            }
        }

        // 삭제 상태
        if (params.deleted) {
            item.delete()
        }

        return item
    }

    /**
     * 식재료 생성
     */
    static REFGroceryItem createFoodIngredient(String name = "식재료") {
        create(name: name, classification: "F")
    }

    /**
     * 레토르트 제품 생성
     */
    static REFGroceryItem createRetortPouch(String name = "레토르트") {
        create(name: name, classification: "R")
    }

    /**
     * 밀키트 생성
     */
    static REFGroceryItem createMealKit(String name = "밀키트") {
        create(name: name, classification: "M")
    }

    /**
     * 상품이 등록된 GroceryItem 생성
     */
    static REFGroceryItem createWithProducts(String name, List<String> products) {
        create(name: name, products: products)
    }

    /**
     * 삭제된 GroceryItem 생성
     */
    static REFGroceryItem createDeleted(String name = "삭제된 상품") {
        create(name: name, deleted: true)
    }

    /**
     * Builder 패턴 진입점
     */
    static GroceryItemBuilder builder() {
        new GroceryItemBuilder()
    }

    /**
     * Fluent Builder
     */
    static class GroceryItemBuilder {
        private Map params = [:]

        GroceryItemBuilder name(String name) {
            params.name = name
            return this
        }

        GroceryItemBuilder imageUrl(String imageUrl) {
            params.imageUrl = imageUrl
            return this
        }

        GroceryItemBuilder classification(String classification) {
            params.classification = classification
            return this
        }

        GroceryItemBuilder foodIngredient() {
            params.classification = "F"
            return this
        }

        GroceryItemBuilder retortPouch() {
            params.classification = "R"
            return this
        }

        GroceryItemBuilder mealKit() {
            params.classification = "M"
            return this
        }

        GroceryItemBuilder withProducts(String... products) {
            params.products = products.toList()
            return this
        }

        GroceryItemBuilder deleted() {
            params.deleted = true
            return this
        }

        REFGroceryItem build() {
            create(params)
        }
    }
}
