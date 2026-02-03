package com.refridge.core_server.groceryItem.domain

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemClassification
import spock.lang.Specification
import spock.lang.Subject

class REFGroceryItemSpec extends Specification{

    @Subject
    REFGroceryItem groceryItem

    def setup() {
        groceryItem = new REFGroceryItem(
                "양파",
                "https://cdn.refridge.com/onion.jpg",
                "F"
        )
    }

}