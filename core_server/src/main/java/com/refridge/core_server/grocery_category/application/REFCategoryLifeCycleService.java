package com.refridge.core_server.grocery_category.application;

import com.refridge.core_server.grocery_category.application.dto.command.REFMajorCategoryCreationCommand;
import com.refridge.core_server.grocery_category.application.dto.command.REFMinorCategoryCreationCommand;
import com.refridge.core_server.grocery_category.application.dto.result.REFCategoryBulkInsertResult;
import com.refridge.core_server.grocery_category.application.dto.result.REFMajorCategoryCreationResult;
import com.refridge.core_server.grocery_category.application.dto.result.REFMinorCategoryCreationResult;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class REFCategoryLifeCycleService {

    private final REFMajorGroceryCategoryRepository majorCategoryRepository;
    private final REFMinorGroceryCategoryRepository minorCategoryRepository;

    @Transactional
    public REFMajorCategoryCreationResult createMajorCategoryByCategory(REFMajorCategoryCreationCommand command) {
        return Optional.ofNullable(command)
                .map(REFMajorCategoryCreationCommand::majorCategoryName)
                .map(name -> REFMajorGroceryCategory.createAndSave(name, majorCategoryRepository))
                .map(REFMajorGroceryCategory::getId)
                .map(REFMajorCategoryCreationResult::new)
                .orElseThrow(() -> new IllegalArgumentException("Invalid major category creation command"));
    }

    @Transactional
    public REFMinorCategoryCreationResult createMinorCategoryByCategory(REFMinorCategoryCreationCommand command) {
        return Optional.ofNullable(command)
                .map(REFMinorCategoryCreationCommand::majorCategoryId)
                .flatMap(majorCategoryRepository::findById)
                .map(majorCategory ->
                        majorCategory.addMinorCategoryAndSaveViaMajorCategory(
                                command.minorCategoryName(),
                                minorCategoryRepository))
                .map(REFMinorGroceryCategory::getId)
                .map(REFMinorCategoryCreationResult::new)
                .orElseThrow(() -> new IllegalArgumentException("Invalid major category creation command"));
    }

    @Transactional
    public REFCategoryBulkInsertResult createMajorCategoryWithBulkMinorCategories(REFMajorCategoryCreationCommand majorCommand, List<REFMinorCategoryCreationCommand> minorCommands) {

        REFMajorGroceryCategory majorCategory = REFMajorGroceryCategory.createAndSave(
                majorCommand.majorCategoryName(), majorCategoryRepository);

        List<REFMinorGroceryCategory> savedMinors = majorCategory
                .addMinorCategoriesAndSaveViaMajorCategory(
                        extractMinorCategoryNames(minorCommands), minorCategoryRepository);

        List<REFMinorCategoryCreationResult> minorResults = savedMinors.stream()
                .map(minor -> new REFMinorCategoryCreationResult(minor.getId()))
                .toList();

        return REFCategoryBulkInsertResult.builder()
                .majorCategoryCreationResult(new REFMajorCategoryCreationResult(majorCategory.getId()))
                .minorCategoryCreationResult(minorResults)
                .build();
    }

    private List<String> extractMinorCategoryNames(List<REFMinorCategoryCreationCommand> commands) {
        return commands.stream()
                .map(REFMinorCategoryCreationCommand::minorCategoryName)
                .toList();
    }
}
