package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.REFGroceryItemCategoryChangeCommand;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class REFGroceryItemCategoricalService {

    private final REFGroceryItemRepository refGroceryItemRepository;

    private final REFGroceryItemCategoryValidatorService refGroceryItemCategoryValidatorService;

    @Transactional
    public void changeCategory(REFGroceryItemCategoryChangeCommand categoryChangeCommand) {
        refGroceryItemRepository.findById(categoryChangeCommand.groceryItemId())
                .ifPresent(groceryItem -> groceryItem.changeCategory(
                        categoryChangeCommand.majorCategoryId(),
                        categoryChangeCommand.minorCategoryId(),
                        refGroceryItemCategoryValidatorService
                ));
    }
}
