package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemUsageMetadataUpdateCommand;
import com.refridge.core_server.groceryItem.application.dto.query.REFGroceryItemMetadataQuery;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemMetadataResult;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemDefaultUnit;
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemUsageMetadata;
import com.refridge.core_server.groceryItem.domain.vo.REFUsageSpecType;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemMetadataDTO;
import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * GroceryItem 사용량 메타데이터 조회·업데이트·디폴트 프로필 적용을 담당하는 애플리케이션 서비스입니다.
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #getMetadata} — fridge_server에 응답할 메타데이터 조회</li>
 *   <li>{@link #updateMetadata} — 관리자/운영 도구의 일괄 갱신</li>
 *   <li>{@link #applyDefaultProfile} — 카테고리 디폴트 프로필 단건 적용 (Layer 1)</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 7. (확장: 2026. 5. 9.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFGroceryItemMetadataService {

    private static final String CSV_DELIMITER = ",";

    private final REFGroceryItemRepository groceryItemRepository;

    @Transactional(readOnly = true)
    public REFGroceryItemMetadataResult getMetadata(REFGroceryItemMetadataQuery query) {
        return Optional.ofNullable(query)
                .map(REFGroceryItemMetadataQuery::groceryItemId)
                .flatMap(groceryItemRepository::findMetadataDTOById)
                .map(this::toResult)
                .orElseThrow(() -> new IllegalArgumentException(
                        "GroceryItem 메타데이터를 찾을 수 없습니다. id=" +
                                Optional.ofNullable(query)
                                        .map(REFGroceryItemMetadataQuery::groceryItemId)
                                        .orElse(null)));
    }

    @Transactional
    public void updateMetadata(REFGroceryItemUsageMetadataUpdateCommand command) {
        Long groceryItemId = command.groceryItemId();

        groceryItemRepository.findById(groceryItemId)
                .ifPresentOrElse(
                        groceryItem -> {
                            REFGroceryItemDefaultUnit defaultUnit =
                                    Optional.ofNullable(command.defaultUnit())
                                            .map(REFGroceryItemDefaultUnit::fromUnitCode)
                                            .orElse(null);

                            List<REFUsageSpecType> allowedUsageTypes =
                                    Optional.ofNullable(command.allowedUsageTypes())
                                            .map(list -> list.stream()
                                                    .map(REFUsageSpecType::fromCode)
                                                    .toList())
                                            .orElse(null);

                            REFUsageSpecType defaultUsageType =
                                    Optional.ofNullable(command.defaultUsageType())
                                            .map(REFUsageSpecType::fromCode)
                                            .orElse(null);

                            warnIfSpoonVolumeOnNonSauceSeasoning(groceryItemId, command.spoonVolumeMl());

                            REFGroceryItemUsageMetadata newMetadata = REFGroceryItemUsageMetadata.of(
                                    defaultUnit,
                                    command.minPortionAmount(),
                                    command.maxPortionAmount(),
                                    command.spoonVolumeMl(),
                                    command.pieceWeightGram(),
                                    allowedUsageTypes,
                                    defaultUsageType
                            );

                            groceryItem.updateUsageMetadata(newMetadata);

                            log.info("[GroceryItem 메타데이터 갱신] id={}, defaultUnit={}, " +
                                            "minPortion={}, maxPortion={}, spoonVolumeMl={}, " +
                                            "pieceWeightGram={}, allowedUsageTypes={}, defaultUsageType={}",
                                    groceryItemId, command.defaultUnit(),
                                    command.minPortionAmount(), command.maxPortionAmount(),
                                    command.spoonVolumeMl(), command.pieceWeightGram(),
                                    command.allowedUsageTypes(), command.defaultUsageType());
                        },
                        () -> {
                            throw new IllegalArgumentException(
                                    "메타데이터 갱신 대상 GroceryItem이 존재하지 않습니다. id=" + groceryItemId);
                        }
                );
    }

    /**
     * 단건 GroceryItem에 카테고리 디폴트 프로필을 적용합니다.
     * <p>
     * Layer 1 자동 매핑의 단위 동작입니다. 부트스트랩 ApplicationRunner가
     * 페이징 단위로 이 메서드를 호출하여 전체 식재료에 일괄 적용합니다.
     * </p>
     *
     * @param groceryItemId    적용 대상 GroceryItem ID
     * @param overrideExisting 기존 메타데이터 보존(false) / 강제 덮어쓰기(true)
     * @return 적용 성공 여부 (프로필 매핑 없거나 비활성 → false)
     */
    @Transactional
    public boolean applyDefaultProfile(Long groceryItemId, boolean overrideExisting) {
        return groceryItemRepository.findMetadataDTOById(groceryItemId)
                .map(REFGroceryItemMetadataDTO::itemType)
                .flatMap(itemType -> groceryItemRepository.findById(groceryItemId)
                        .map(grocery -> grocery.applyDefaultProfile(itemType, overrideExisting)))
                .orElse(false);
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    private REFGroceryItemMetadataResult toResult(REFGroceryItemMetadataDTO dto) {
        String categoryString = Optional.ofNullable(dto.itemType())
                .map(REFInventoryItemType::name)
                .orElse(null);

        List<String> allowedUsageTypes = Optional.ofNullable(dto.allowedUsageTypesCsv())
                .filter(csv -> !csv.isBlank())
                .map(csv -> Arrays.stream(csv.split(CSV_DELIMITER))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList())
                .orElse(null);

        return REFGroceryItemMetadataResult.builder()
                .id(dto.id())
                .name(dto.name())
                .category(categoryString)
                .defaultUnit(dto.defaultUnit())
                .minPortionAmount(dto.minPortionAmount())
                .maxPortionAmount(dto.maxPortionAmount())
                .spoonVolumeMl(dto.spoonVolumeMl())
                .pieceWeightGram(dto.pieceWeightGram())
                .allowedUsageTypes(allowedUsageTypes)
                .defaultUsageType(dto.defaultUsageType())
                .build();
    }

    private void warnIfSpoonVolumeOnNonSauceSeasoning(Long groceryItemId, Integer spoonVolumeMl) {
        if (spoonVolumeMl == null) {
            return;
        }
        groceryItemRepository.findMetadataDTOById(groceryItemId)
                .map(REFGroceryItemMetadataDTO::itemType)
                .filter(itemType -> itemType != REFInventoryItemType.SAUCE_SEASONING)
                .ifPresent(itemType -> log.warn(
                        "[GroceryItem 메타데이터 갱신] SAUCE_SEASONING이 아닌 itemType({})에 " +
                                "spoonVolumeMl이 설정됩니다. groceryItemId={}",
                        itemType, groceryItemId));
    }
}