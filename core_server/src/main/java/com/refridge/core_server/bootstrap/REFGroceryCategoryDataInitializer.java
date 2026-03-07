package com.refridge.core_server.bootstrap;

import com.refridge.core_server.grocery_category.application.REFCategoryLifeCycleService;
import com.refridge.core_server.grocery_category.application.dto.command.REFMajorCategoryCreationCommand;
import com.refridge.core_server.grocery_category.application.dto.command.REFMinorCategoryCreationCommand;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.ar.REFMajorGroceryCategory;
import com.refridge.core_server.bootstrap.dto.REFCategoryInitRawDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 애플리케이션 시작 시 GroceryCategory 초기 데이터를 삽입하는 Initializer.
 *
 * <p><b>정합성 체크 전략 (2단계):</b>
 * <ol>
 *   <li>대분류가 없으면 → 대분류 + 하위 중분류 전체 삽입</li>
 *   <li>대분류는 있는데 중분류 일부가 누락됐으면 → 누락된 중분류만 선택 삽입</li>
 *   <li>대분류와 모든 중분류가 이미 존재하면 → 스킵</li>
 * </ol>
 *
 * <p><b>멱등성:</b> 서버를 몇 번 재시작해도 중복 삽입이 발생하지 않습니다.
 * 부분 삽입 상태(서버 강제 종료 등)에서 재시작해도 누락분만 자동 보완됩니다.
 *
 * <p><b>실행 순서:</b> {@code @Order(1)} — GroceryItem 등 카테고리를 FK로 참조하는
 * 다른 Initializer보다 반드시 먼저 실행되어야 합니다.
 *
 * <p><b>데이터 위치:</b> {@code resources/init/grocery_category_init_data.json}
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class REFGroceryCategoryDataInitializer implements ApplicationRunner {

    private static final String INIT_DATA_PATH = "init/grocery_category_init_data.json";

    private final REFCategoryLifeCycleService categoryLifeCycleService;
    private final REFMajorGroceryCategoryRepository majorCategoryRepository;
    private final REFMinorGroceryCategoryRepository minorCategoryRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, REFCategoryInitRawDto> rawData = loadRawData();
            log.info("[CategoryInit] 초기화 시작 - JSON 대분류 {}개", rawData.size());

            rawData.forEach(this::processCategory);

            log.info("[CategoryInit] 초기화 완료.");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "[CategoryInit] 초기 데이터 파일을 읽는 데 실패했습니다: " + INIT_DATA_PATH, e);
        }
    }

    /**
     * 대분류 단위로 정합성을 체크하고 필요한 경우에만 삽입한다.<p>
     * - 대분류 없음 → 전체 삽입<p>
     * - 대분류 있음 → 누락된 중분류만 보완 삽입
     */
    private void processCategory(String majorName, REFCategoryInitRawDto dto) {
        majorCategoryRepository.findByName(majorName)
                .ifPresentOrElse(
                        existingMajor -> fillMissingMinorCategories(majorName, existingMajor, dto.items()),
                        () -> insertMajorWithAllMinors(majorName, dto)
                );
    }

    /* 대분류 없음 → 대분류 + 중분류 전체를 Service에 위임하여 삽입 */
    private void insertMajorWithAllMinors(String majorName, REFCategoryInitRawDto dto) {
        REFMajorCategoryCreationCommand majorCommand = buildMajorCommand(majorName, dto.typeGroup());
        List<REFMinorCategoryCreationCommand> minorCommands = buildMinorCommands(dto.items(), null);

        categoryLifeCycleService.createMajorCategoryWithBulkMinorCategories(majorCommand, minorCommands);
        log.info("[CategoryInit] 대분류 삽입 완료 - '{}' (중분류 {}개)", majorName, minorCommands.size());
    }

    /* 대분류 있음 → 누락된 중분류만 Service에 위임하여 보완 삽입 */
    private void fillMissingMinorCategories(String majorName,
                                            REFMajorGroceryCategory existingMajor,
                                            List<REFCategoryInitRawDto.ItemRaw> items) {
        Set<String> existingMinorNames = minorCategoryRepository
                .findMinorCategoryNamesByMajorId(existingMajor.getId());

        List<REFMinorCategoryCreationCommand> missingCommands = buildMinorCommands(items, existingMinorNames);

        if (missingCommands.isEmpty()) {
            log.debug("[CategoryInit] '{}' - 모든 중분류가 이미 존재합니다. 스킵.", majorName);
            return;
        }

        log.info("[CategoryInit] '{}' - 누락된 중분류 {}개 보완 삽입", majorName, missingCommands.size());
        missingCommands.forEach(command ->
                categoryLifeCycleService.createMinorCategoryByCategory(
                        command.toBuilder().majorCategoryId(existingMajor.getId()).build()
                )
        );
    }

    /* ---- Command 빌더 ---- */

    private REFMajorCategoryCreationCommand buildMajorCommand(String majorName, String typeGroupRaw) {
        return REFMajorCategoryCreationCommand.builder()
                .majorCategoryName(majorName)
                .majorCategoryTypeGroupName(typeGroupRaw)
                .build();
    }

    /**
     * 중분류 Command 리스트를 빌드한다.
     *
     * @param items              JSON에서 파싱된 중분류 항목 목록
     * @param existingMinorNames 이미 DB에 존재하는 중분류 이름 Set.
     *                           null이면 필터링 없이 전체 변환 (대분류 신규 삽입 경로)
     */
    private List<REFMinorCategoryCreationCommand> buildMinorCommands(
            List<REFCategoryInitRawDto.ItemRaw> items,
            Set<String> existingMinorNames) {
        return items.stream()
                .filter(item -> existingMinorNames == null || !existingMinorNames.contains(item.name()))
                .map(item -> REFMinorCategoryCreationCommand.builder()
                        .minorCategoryName(item.name())
                        .itemType(item.itemType())
                        .build())
                .toList();
    }

    /* ---- JSON 로딩 ---- */

    private Map<String, REFCategoryInitRawDto> loadRawData() throws IOException {
        ClassPathResource resource = new ClassPathResource(INIT_DATA_PATH);
        return objectMapper.readValue(
                resource.getInputStream(),
                new TypeReference<>() {
                }
        );
    }
}