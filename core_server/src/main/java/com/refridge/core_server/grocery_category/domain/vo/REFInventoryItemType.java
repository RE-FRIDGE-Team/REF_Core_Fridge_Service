package com.refridge.core_server.grocery_category.domain.vo;

public enum REFInventoryItemType {
    ALCOHOL, RAW_PRODUCE, GRAIN, SIMPLE_PROCESSED,
    RAW_MEAT, PROCESSED_MEAT,
    RAW_SEAFOOD, DRIED_SEAFOOD,
    RAW_FRUIT, RTC_MEAL, INSTANT, FROZEN_MEAL,
    SAUCE_SEASONING, BEVERAGE, DAIRY,
    BAKERY, SNACK, CANNED;

    public static REFInventoryItemType from(String type) {
        try {
            return REFInventoryItemType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid inventory item type: " + type);
        }
    }
}
