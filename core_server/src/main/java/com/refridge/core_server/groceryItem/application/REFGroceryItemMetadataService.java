package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.command.REFGroceryItemUsageMetadataUpdateCommand;
import com.refridge.core_server.groceryItem.application.dto.query.REFGroceryItemMetadataQuery;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemMetadataResult;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemSpecialCaseResolver;
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemUsageMetadata;
import com.refridge.core_server.groceryItem.domain.vo.REFUsageUnit;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemBootstrapDTO;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemMetadataDTO;
import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * GroceryItem 사용량 메타데이터 조회·업데이트·프로필 적용을 담당하는 애플리케이션 서비스입니다.
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #getMetadata} — fridge_server에 응답할 메타데이터 조회</li>
 *   <li>{@link #updateMetadata} — 관리자/운영 도구의 일괄 갱신</li>
 *   <li>{@link #applyDefaultProfile} — 단건 카테고리 디폴트 프로필 적용 (Layer 1)</li>
 *   <li>{@link #applyDefaultProfileFromBootstrap} — 부트스트랩용 (이미 categoryName 알고 있을 때)</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFGroceryItemMetadataService {

    private static final String CSV_DELIMITER = ",";

    private final REFGroceryItemRepository groceryItemRepository;
    private final REFGroceryItemSpecialCaseResolver specialCaseResolver;

    /* ──────────────────── QUERY ──────────────────── */

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

    /* ──────────────────── UPDATE ──────────────────── */

    @Transactional
    public void updateMetadata(REFGroceryItemUsageMetadataUpdateCommand command) {
        Long groceryItemId = command.groceryItemId();

        groceryItemRepository.findById(groceryItemId)
                .ifPresentOrElse(
                        groceryItem -> {
                            REFUsageUnit defaultUnit =
                                    Optional.ofNullable(command.defaultUnit())
                                            .map(REFUsageUnit::fromCode)
                                            .orElse(null);

                            List<REFUsageUnit> allowedUsageUnits =
                                    Optional.ofNullable(command.allowedUsageUnits())
                                            .map(list -> list.stream()
                                                    .map(REFUsageUnit::fromCode)
                                                    .toList())
                                            .orElse(null);

                            REFUsageUnit defaultUsageUnit =
                                    Optional.ofNullable(command.defaultUsageUnit())
                                            .map(REFUsageUnit::fromCode)
                                            .orElse(null);

                            REFGroceryItemUsageMetadata newMetadata = REFGroceryItemUsageMetadata.of(
                                    defaultUnit,
                                    command.minPortionAmount(),
                                    command.maxPortionAmount(),
                                    command.pieceWeightGram(),
                                    allowedUsageUnits,
                                    defaultUsageUnit
                            );

                            groceryItem.updateUsageMetadata(newMetadata);

                            log.info("[GroceryItem 메타데이터 갱신] id={}, defaultUnit={}, " +
                                            "min/maxPortion={}/{}, pieceWeightGram={}, " +
                                            "allowedUsageUnits={}, defaultUsageUnit={}",
                                    groceryItemId, command.defaultUnit(),
                                    command.minPortionAmount(), command.maxPortionAmount(),
                                    command.pieceWeightGram(),
                                    command.allowedUsageUnits(), command.defaultUsageUnit());
                        },
                        () -> {
                            throw new IllegalArgumentException(
                                    "메타데이터 갱신 대상 GroceryItem이 존재하지 않습니다. id=" + groceryItemId);
                        }
                );
    }

    /* ──────────────────── APPLY DEFAULT PROFILE ──────────────────── */

    /**
     * 단건 GroceryItem에 카테고리 디폴트 프로필 + 특수 케이스 오버라이드를 적용합니다.
     * <p>
     * 운영 중 새로 생성된 GroceryItem에 단건 적용하는 용도. 부트스트랩 Runner는
     * {@link #applyDefaultProfileFromBootstrap}을 사용합니다 (categoryName을 이미 알고 있을 때 효율).
     * </p>
     *
     * @return 적용 성공 여부 (프로필 매핑 없거나 비활성 → false)
     */
    @Transactional
    public boolean applyDefaultProfile(Long groceryItemId, boolean overrideExisting) {
        return groceryItemRepository.findMetadataDTOById(groceryItemId)
                .flatMap(meta -> groceryItemRepository.findDetailDTOById(groceryItemId)
                        .map(detail -> applyProfileWithCategoryLookup(
                                groceryItemId, meta, detail.majorCategoryId(), detail.minorCategoryId(),
                                overrideExisting)))
                .orElse(false);
    }

    /**
     * 부트스트랩 전용 — categoryName과 품목명을 이미 알고 있을 때.
     * <p>
     * 카테고리 ID로 다시 이름을 조회하지 않고 바로 프로필 적용 + 특수 케이스 오버라이드 수행.
     * </p>
     */
    @Transactional
    public boolean applyDefaultProfileFromBootstrap(
            REFGroceryItemBootstrapDTO bootstrapDto, boolean overrideExisting) {

        return groceryItemRepository.findById(bootstrapDto.groceryItemId())
                .map(grocery -> applyProfileAndOverrides(
                        grocery,
                        bootstrapDto.minorCategoryName(),
                        bootstrapDto.majorCategoryName(),
                        bootstrapDto.groceryItemName(),
                        overrideExisting))
                .orElse(false);
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    /**
     * AR에 프로필 적용 후 특수 케이스 resolver로 추가 오버라이드.
     */
    private boolean applyProfileAndOverrides(
            REFGroceryItem grocery,
            String minorCategoryName, String majorCategoryName, String groceryItemName,
            boolean overrideExisting) {

        boolean applied = grocery.applyDefaultProfile(
                minorCategoryName, majorCategoryName, overrideExisting);

        if (applied) {
            // 카테고리 프로필 적용 결과에 품목명 기반 오버라이드 추가
            REFGroceryItemUsageMetadata afterProfile = grocery.getUsageMetadataOrEmpty();
            REFGroceryItemUsageMetadata afterOverrides =
                    specialCaseResolver.applyOverrides(groceryItemName, afterProfile);
            grocery.updateUsageMetadata(afterOverrides);
        }

        return applied;
    }

    /**
     * 단건 apply 경로 — categoryId → categoryName 조회가 추가 필요.
     * <p>
     * 현재 구현에서는 Detail DTO가 categoryName을 포함하지 않으므로,
     * 별도 조회 메서드를 추가해야 합니다. 임시로 부트스트랩 페이지 쿼리를 재활용해
     * id 기반 단건 조회처럼 사용. 운영에서는 단건 호출 빈도가 낮으므로 허용.
     * </p>
     */
    private boolean applyProfileWithCategoryLookup(
            Long groceryItemId, REFGroceryItemMetadataDTO meta,
            Long majorCategoryId, Long minorCategoryId,
            boolean overrideExisting) {

        // findBootstrapPageAfter를 cursor=groceryItemId-1, size=1로 호출하면 정확히 그 항목만 가져옴
        List<REFGroceryItemBootstrapDTO> page =
                groceryItemRepository.findBootstrapPageAfter(groceryItemId - 1, 1);

        if (page.isEmpty() || !page.get(0).groceryItemId().equals(groceryItemId)) {
            log.warn("[프로필 적용] 단건 조회 실패. id={}", groceryItemId);
            return false;
        }

        return applyDefaultProfileFromBootstrap(page.get(0), overrideExisting);
    }

    private REFGroceryItemMetadataResult toResult(REFGroceryItemMetadataDTO dto) {
        String categoryString = Optional.ofNullable(dto.itemType())
                .map(REFInventoryItemType::name)
                .orElse(null);

        List<String> allowedUsageUnits = Optional.ofNullable(dto.allowedUsageUnitsCsv())
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
                .pieceWeightGram(dto.pieceWeightGram())
                .allowedUsageUnits(allowedUsageUnits)
                .defaultUsageUnit(dto.defaultUsageUnit())
                .build();
    }
}