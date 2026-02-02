package com.refridge.core_server.groceryItem.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ref_grocery_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String groceryItemName;


}
