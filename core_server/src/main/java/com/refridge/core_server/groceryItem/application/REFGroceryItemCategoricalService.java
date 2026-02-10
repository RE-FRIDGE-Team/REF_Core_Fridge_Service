package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemCategoryChangeCommand;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class REFGroceryItemCategoricalService {

    private final REFGroceryItemRepository refGroceryItemRepository;

    private final REFGroceryItemCategoryValidatorService refGroceryItemCategoryValidatorService;

    private final REFMinorGroceryCategoryRepository refMinorGroceryCategoryRepository;

    @Transactional
    public void changeCategory(REFGroceryItemCategoryChangeCommand categoryChangeCommand) {
        refGroceryItemRepository.findById(categoryChangeCommand.groceryItemId())
                .ifPresent(groceryItem -> groceryItem.changeCategory(
                        categoryChangeCommand.majorCategoryId(),
                        categoryChangeCommand.minorCategoryId(),
                        refGroceryItemCategoryValidatorService
                ));
    }

    public Optional<String> getCategoryFullNameWithSeparator(Long minorCategoryId) {
        return Optional.of(refMinorGroceryCategoryRepository.findById(minorCategoryId)
                .map(REFMinorGroceryCategory::formatFullCategoryPath))
                .orElseGet(Optional::empty);
    }
}
