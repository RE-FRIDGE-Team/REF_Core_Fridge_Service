package com.refridge.core_server.groceryItem.domain.service;

import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class REFGroceryItemCategoryValidateAndAdaptService {

    private final REFMajorGroceryCategoryRepository majorGroceryCategoryRepository;

    private final REFMinorGroceryCategoryRepository minorGroceryCategoryRepository;

    public boolean isValidCategoryIds(Long majorCategoryId, Long minorCategoryId) {
        return Optional.of(minorGroceryCategoryRepository.findById(minorCategoryId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(minorCategory -> minorCategory.checkOwnMajorCategory(majorCategoryId))
                .orElse(false);
    }

    public Long findMajorCategoryIdByName(String majorCategoryName){
        return majorGroceryCategoryRepository.findCategoryIdByName(majorCategoryName)
                .orElseThrow(() -> new IllegalArgumentException("대분류 카테고리 이름이 유효하지 않습니다: " + majorCategoryName));
    }

    public Long findMinorCategoryIdByName(String minorCategoryName){
        return minorGroceryCategoryRepository.findCategoryIdByName(minorCategoryName)
                .orElseThrow(() -> new IllegalArgumentException("중분류 카테고리 이름이 유효하지 않습니다: " + minorCategoryName));
    }
}
