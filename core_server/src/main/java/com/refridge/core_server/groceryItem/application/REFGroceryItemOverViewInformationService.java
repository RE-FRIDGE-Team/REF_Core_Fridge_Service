package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.REFGroceryItemGetSummaryInfoCommand;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class REFGroceryItemOverViewInformationService {

    private final REFGroceryItemRepository refGroceryItemRepository;

    private final REFGroceryItemCategoryValidatorService categoryValidatorService;

    public void getGroceryItemSummaryOverViewInformation(REFGroceryItemGetSummaryInfoCommand command) {

    }
}
