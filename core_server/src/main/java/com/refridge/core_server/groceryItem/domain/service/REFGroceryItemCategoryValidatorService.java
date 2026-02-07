package com.refridge.core_server.groceryItem.domain.service;

import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
