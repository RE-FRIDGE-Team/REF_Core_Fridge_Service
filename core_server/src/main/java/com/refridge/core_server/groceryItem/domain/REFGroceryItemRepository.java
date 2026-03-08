package com.refridge.core_server.groceryItem.domain;

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;


public interface REFGroceryItemRepository extends JpaRepository<REFGroceryItem, Long>, REFGroceryItemRepositoryCustom{

    @Query("SELECT g.groceryItemName.value FROM REFGroceryItem g")
    Set<String> findAllGroceryItemNames();
}
