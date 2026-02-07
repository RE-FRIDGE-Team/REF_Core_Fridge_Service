package com.refridge.core_server.groceryItem.domain.service;

import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class REFGroceryItemCategoryValidatorService {

    private final REFMajorGroceryCategoryRepository majorGroceryCategoryRepository;

    private final REFMinorGroceryCategoryRepository minorGroceryCategoryRepository;

    public boolean isValidMajorCategoryId(Long majorCategoryId){
        return majorGroceryCategoryRepository.existsById(majorCategoryId);
    }

    public boolean isValidMinorCategoryId(Long minorCategoryId){
        return minorGroceryCategoryRepository.existsById(minorCategoryId);
    }

    public boolean isValidCategoryIds(Long majorCategoryId, Long minorCategoryId) {
        return Optional.of(minorGroceryCategoryRepository.findById(minorCategoryId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(minorCategory -> minorCategory.checkOwnMajorCategory(majorCategoryId))
                .orElse(false);
    }
}
