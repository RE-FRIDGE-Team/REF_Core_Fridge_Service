package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.REFGroceryImageUpdateCommand;
import com.refridge.core_server.groceryItem.application.dto.REFGroceryItemNameUpdateCommand;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class REFGroceryItemInformationUpdateService {

    private final REFGroceryItemRepository refGroceryItemRepository;

    @Transactional
    public void modifyGroceryItemName(REFGroceryItemNameUpdateCommand nameUpdateCommand) {
        Optional.of(nameUpdateCommand)
                .flatMap(cmd -> refGroceryItemRepository.findById(cmd.groceryItemId()))
                .ifPresent(groceryItem -> groceryItem.changeGroceryItemName(nameUpdateCommand.name()));
    }


    @Transactional
    public void modifyGroceryItemRepresentativeImage(REFGroceryImageUpdateCommand imageUpdateCommand) {
        Optional.of(imageUpdateCommand)
                .flatMap(cmd -> refGroceryItemRepository.findById(cmd.groceryItemId()))
                .ifPresent(groceryItem -> groceryItem.changeRepresentativeImage(imageUpdateCommand.representativeImageUrl()));
    }


}
