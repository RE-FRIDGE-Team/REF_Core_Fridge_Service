package com.refridge.core_server.groceryItem.domain;

import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import org.springframework.data.jpa.repository.JpaRepository;


public interface REFGroceryItemRepository extends JpaRepository<REFGroceryItem, Long>, REFGroceryItemRepositoryCustom{
}
