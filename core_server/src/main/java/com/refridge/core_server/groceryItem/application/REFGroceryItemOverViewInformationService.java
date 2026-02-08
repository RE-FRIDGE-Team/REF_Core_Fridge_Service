package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.REFGroceryItemGetSummaryInfoCommand;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class REFGroceryItemOverViewInformationService {

    private final REFGroceryItemRepository refGroceryItemRepository;

    private final REFGroceryItemCategoryValidatorService categoryValidatorService;

    @Transactional(readOnly = true)
    public void getGroceryItemSummaryOverViewInformation(REFGroceryItemGetSummaryInfoCommand command) {

    }
}
