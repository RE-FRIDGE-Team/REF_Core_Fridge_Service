package com.refridge.core_server.grocery_category.application;

import com.refridge.core_server.grocery_category.application.dto.command.REFMajorCategoryCreationCommand;
import com.refridge.core_server.grocery_category.application.dto.command.REFMajorCategoryRemoveCommand;
import com.refridge.core_server.grocery_category.application.dto.command.REFMinorCategoryCreationCommand;
import com.refridge.core_server.grocery_category.application.dto.command.REFMinorCategoryRemoveCommand;
import com.refridge.core_server.grocery_category.application.dto.result.REFCategoryBulkInsertResult;
import com.refridge.core_server.grocery_category.application.dto.result.REFMajorCategoryCreationResult;
import com.refridge.core_server.grocery_category.application.dto.result.REFMinorCategoryCreationResult;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
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

    /**
     * 대분류 카테고리를 생성합니다.
     *
     * @param command 대분류 카테고리 생성 요청
     * @return REFMajorCategoryCreationResult
     */
    @Transactional
    public REFMajorCategoryCreationResult createMajorCategoryByCategory(REFMajorCategoryCreationCommand command) {
        return Optional.ofNullable(command)
                .map(REFMajorCategoryCreationCommand::majorCategoryName)
                .map(name -> REFMajorGroceryCategory.createAndSave(name, majorCategoryRepository))
                .map(REFMajorGroceryCategory::getId)
                .map(REFMajorCategoryCreationResult::new)
                .orElseThrow(() -> new IllegalArgumentException("Invalid major category creation command"));
    }

    /**
     * 소분류 카테고리를 생성합니다. 요청에 대분류 카테고리에 대한 정보가 들어있으며, 해당 대분류 카테고리에 소분류 카테고리를 추가합니다.
     *
     * @param command 소분류 카테고리 생성 요청
     * @return REFMinorCategoryCreationResult
     */
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

    /**
     * 단일 대분류 카테고리와 다수의 소분류 카테고리를 한 번에 생성합니다.
     *
     * @param majorCommand  대분류 카테고리 생성 명령
     * @param minorCommands 중분류 카테고리 생성 명령 리스트
     * @return REFCategoryBulkInsertResult
     */
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

    @Transactional
    public void deleteMinorCategoryById(REFMinorCategoryRemoveCommand command) {
        REFMinorGroceryCategory removeTargetMinorCategory = Optional.of(command)
                .map(REFMinorCategoryRemoveCommand::minorCategoryId)
                .flatMap(minorCategoryRepository::findById)
                .orElseThrow(() -> new IllegalArgumentException("Invalid minor category ID"));

        Optional.of(command)
                .map(REFMinorCategoryRemoveCommand::majorCategoryId)
                .flatMap(majorCategoryRepository::findById)
                .map(majorCategory ->
                        majorCategory.removeMinorCategoryAndDelete(removeTargetMinorCategory, minorCategoryRepository))
                .orElseThrow(() -> new IllegalArgumentException("Invalid major category ID"));

        // TODO : 중분류 카테고리 제거 시 연관된 식재료들의 카테고리 처리 로직 필요, 중분류 삭제 이벤트 발행
    }

    @Transactional
    public void deleteMinorCategoryByName(REFMinorCategoryRemoveCommand command) {
        Optional.of(command)
                .map(REFMinorCategoryRemoveCommand::majorCategoryId)
                .flatMap(majorCategoryRepository::findById)
                .map(majorCategory ->
                        majorCategory.removeMinorCategoryByNameAndDelete(command.minorCategoryName(), minorCategoryRepository))
                .orElseThrow(() -> new IllegalArgumentException("Invalid major category ID"));

        // TODO : 중분류 카테고리 제거 시 연관된 식재료들의 카테고리 처리 로직 필요, 중분류 삭제 이벤트 발행
    }

    private List<String> extractMinorCategoryNames(List<REFMinorCategoryCreationCommand> commands) {
        return commands.stream()
                .map(REFMinorCategoryCreationCommand::minorCategoryName)
                .toList();
    }
}
