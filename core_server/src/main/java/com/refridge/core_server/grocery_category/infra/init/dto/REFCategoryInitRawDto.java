package com.refridge.core_server.grocery_category.infra.init.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * grocery_category_init_data.json 파싱용 DTO.
 * Application/Domain 레이어와 완전히 분리된 infra 전용 객체입니다.
 *
 * <pre>
 * {@code
 * {
 *   "술": { "type_group": "ALCOHOL",
 *          "items": [ { "name": "와인",
 *                       "item_type": "ALCOHOL" } ]
 *        }
 * }
 * }
 * </pre>
 */
public record REFCategoryInitRawDto(

        @JsonProperty("type_group")
        String typeGroup,

        @JsonProperty("items")
        List<ItemRaw> items
) {

    public record ItemRaw(
            @JsonProperty("name")
            String name,

            @JsonProperty("item_type")
            String itemType
    ) {}
}