package com.refridge.core_server.grocery_category.application;

import com.refridge.core_server.grocery_category.application.dto.command.REFMajorCategoryCreationCommand;
import com.refridge.core_server.grocery_category.application.dto.command.REFMinorCategoryCreationCommand;
import com.refridge.core_server.grocery_category.application.dto.command.REFMinorCategoryRemoveCommand;
import com.refridge.core_server.grocery_category.application.dto.result.REFCategoryBulkCreationResult;
import com.refridge.core_server.grocery_category.application.dto.result.REFMajorCategoryCreationResult;
import com.refridge.core_server.grocery_category.application.dto.result.REFMinorCategoryCreationResult;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory;
import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import com.refridge.core_server.grocery_category.domain.vo.REFMinorCategoryCreationData;
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
                // 비즈니스 로직에 따른 대분류 카테고리 생성 및 저장
                .map(this::createAndSaveMajorCategoryByCommand)
                // 생성된 카테고리 아이디 확보
                .map(REFMajorGroceryCategory::getId)
                // 결과 DTO로 변환
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
                // 요청에서 대분류 카테고리 ID 추출 및 해당 ID로 대분류 카테고리 조회
                .map(REFMinorCategoryCreationCommand::majorCategoryId)
                .flatMap(majorCategoryRepository::findById)
                // 대분류 카테고리가 존재할 경우, 소분류 카테고리 생성 및 저장 로직 수행
                .map(majorCategory -> this.createAndSaveMinorCategoryByCommandAndMajorCategory(majorCategory, command))
                // 생성된 소분류 카테고리로 생성 완료 결과 DTO로 변환
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
    public REFCategoryBulkCreationResult createMajorCategoryWithBulkMinorCategories(
            REFMajorCategoryCreationCommand majorCommand,
            List<REFMinorCategoryCreationCommand> minorCommands) {

        return Optional.ofNullable(majorCommand)
                // 대분류 카테고리 생성 및 저장
                .map(this::createAndSaveMajorCategoryByCommand)
                // 생성된 대분류 카테고리를 활용하여 다수의 소분류 카테고리 생성 및 저장, 그리고 결과 DTO로 변환
                .map(majorCategory -> createAndSaveMinorCategoriesAndBuildBulkCreationResult(majorCategory, minorCommands))
                .orElseThrow(() -> new IllegalArgumentException("Invalid major category creation command"));
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

    /* INTERNAL METHOD : Command의 내용을 통해 새로운 대분류 카테고리를 생성한다. */
    private REFMajorGroceryCategory createAndSaveMajorCategoryByCommand(REFMajorCategoryCreationCommand command) {
        return REFMajorGroceryCategory.createAndSave(
                command.majorCategoryName(),
                command.majorCategoryTypeGroupName(),
                majorCategoryRepository);
    }

    /* INTERNAL METHOD : Command와 MajorCategory를 통해 새로운 중분류 카테고리를 생성한다. */
    private REFMinorGroceryCategory createAndSaveMinorCategoryByCommandAndMajorCategory(REFMajorGroceryCategory majorCategory, REFMinorCategoryCreationCommand command) {
        return majorCategory.addMinorCategoryAndSaveViaMajorCategory(
                command.minorCategoryName(),
                command.itemType(),
                minorCategoryRepository);
    }

    private REFCategoryBulkCreationResult createAndSaveMinorCategoriesAndBuildBulkCreationResult(
            REFMajorGroceryCategory majorCategory,
            List<REFMinorCategoryCreationCommand> minorCommands) {

        List<REFMinorCategoryCreationResult> minorResults = majorCategory
                .addMinorCategoriesAndSaveViaMajorCategory(toCreationDataList(minorCommands), minorCategoryRepository)
                .stream()
                .map(minor -> new REFMinorCategoryCreationResult(minor.getId()))
                .toList();

        return REFCategoryBulkCreationResult.builder()
                .majorCategoryCreationResult(new REFMajorCategoryCreationResult(majorCategory.getId()))
                .minorCategoryCreationResult(minorResults)
                .build();
    }

    /* INTERNAL METHOD : Command 리스트를 도메인 레이어의 CreationData 리스트로 변환한다. */
    private List<REFMinorCategoryCreationData> toCreationDataList(List<REFMinorCategoryCreationCommand> commands) {
        return commands.stream()
                .map(cmd -> new REFMinorCategoryCreationData(cmd.minorCategoryName(), REFInventoryItemType.valueOf(cmd.itemType())))
                .toList();
    }
}