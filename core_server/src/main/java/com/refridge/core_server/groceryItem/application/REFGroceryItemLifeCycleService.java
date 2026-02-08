package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemCreateCommand;
import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemDeleteCommand;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class REFGroceryItemLifeCycleService {

    private final REFGroceryItemRepository refGroceryItemRepository;
    private final REFGroceryItemCategoryValidatorService refGroceryItemCategoryValidatorService;

    @Transactional
    public Long createGroceryItem(REFGroceryItemCreateCommand createCommand) {
        return Optional.of(createCommand)
                .map(this::createAndSaveEntityFromCommand)
                .map(REFGroceryItem::getId)
                .orElseThrow(() -> new IllegalStateException("Grocery Item 생성에 실패했습니다."));

    }

    @Transactional
    public List<Long> createGroceryItemBulk(List<REFGroceryItemCreateCommand> createCommands) {
        List<REFGroceryItem> commandItemBulk = Optional.ofNullable(createCommands)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(command -> command.toEntity(refGroceryItemCategoryValidatorService))
                .toList();

        return Stream.of(refGroceryItemRepository.saveAll(commandItemBulk))
                .flatMap(List::stream)
                .map(REFGroceryItem::getId)
                .toList();
    }

    @Transactional
    public void deleteGroceryItem(REFGroceryItemDeleteCommand deleteCommand) {
        Optional.of(deleteCommand)
                .map(REFGroceryItemDeleteCommand::groceryItemId)
                .flatMap(refGroceryItemRepository::findById)
                .ifPresent(REFGroceryItem::delete);
    }

    @Transactional
    public void deleteGroceryItemBulk(List<REFGroceryItemDeleteCommand> deleteCommands) {
        Optional.ofNullable(deleteCommands)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(REFGroceryItemDeleteCommand::groceryItemId)
                .map(refGroceryItemRepository::findById)
                .flatMap(Optional::stream)
                .forEach(REFGroceryItem::delete);
    }


    /* Internal Method : Domain 영역의 CreateAndSave를 짧게 사용할 수 있는 재사용성 높은 메서드 */
    private REFGroceryItem createAndSaveEntityFromCommand(REFGroceryItemCreateCommand cmd) {
        return REFGroceryItem.createAndSave(
                cmd.groceryItemName(),
                cmd.representativeImageUrl(),
                cmd.groceryItemClassification(),
                cmd.majorCategoryId(),
                cmd.minorCategoryId(),
                refGroceryItemRepository, refGroceryItemCategoryValidatorService);
    }
}
