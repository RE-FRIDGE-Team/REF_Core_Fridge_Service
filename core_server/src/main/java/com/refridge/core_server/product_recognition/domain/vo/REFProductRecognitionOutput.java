package com.refridge.core_server.product_recognition.domain.vo;

import com.mysema.commons.lang.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFProductRecognitionOutput {

    @Column(name = "result_grocery_item_id")
    private Long groceryItemId;

    @Column(name = "result_grocery_item_name")
    private String groceryItemName;

    @Column(name = "result_category_path")
    private String categoryPath;

    @Column(name = "result_image_url")
    private String imageUrl;

    public static REFProductRecognitionOutput of(Long groceryItemId, String groceryItemName,
                                       String categoryPath, String imageUrl) {
        Assert.notNull(groceryItemId, "groceryItemId must not be null");
        Assert.hasText(groceryItemName, "groceryItemName must not be empty");
        Assert.hasText(categoryPath, "categoryPath must not be empty");
        return new REFProductRecognitionOutput(groceryItemId, groceryItemName, categoryPath, imageUrl);
    }
}
