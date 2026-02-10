package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.query.REFGroceryItemInformationQuery;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemDetailInfoResult;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemSummaryInfoResult;
import com.refridge.core_server.groceryItem.application.mapper.REFGroceryItemInformationMapper;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;

import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemDetailDTO;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.ar.REFMinorGroceryCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class REFGroceryItemQueryService {

    private final REFGroceryItemRepository refGroceryItemRepository;

    private final REFGroceryItemInformationMapper refGroceryItemInformationMapper;

    private final REFGroceryItemCategoricalService refGroceryItemCategoricalService;

    private final REFMinorGroceryCategoryRepository refMinorGroceryCategoryRepository;

    @Transactional(readOnly = true)
    public REFGroceryItemSummaryInfoResult getGroceryItemSummaryOverViewInformation(REFGroceryItemInformationQuery query) {
        return Optional.ofNullable(query)
                .filter(q -> q.groceryItemId() != null)
                .map(REFGroceryItemInformationQuery::groceryItemId)
                .flatMap(refGroceryItemRepository::findSummarizedDTOById)
                .map(refGroceryItemInformationMapper::toSummaryResult)
                .orElseThrow(() -> new IllegalStateException("Grocery Item 요약 정보 조회에 실패했습니다."));
    }

    @Transactional(readOnly = true)
    public REFGroceryItemDetailInfoResult getGroceryItemDetailOverViewInformation(REFGroceryItemInformationQuery query) {
        return Optional.ofNullable(query)
                .filter(q -> q.groceryItemId() != null)
                .map(REFGroceryItemInformationQuery::groceryItemId)
                .flatMap(refGroceryItemRepository::findDetailDTOById)
                .map(this::mappingResultFromDto)
                .orElseThrow(() -> new IllegalStateException("Grocery Item 상세 정보 조회에 실패했습니다."));
    }

    @Transactional(readOnly = true)
    public List<REFGroceryItemSummaryInfoResult> getGroceryItemSummaryOverViewInformationList(List<REFGroceryItemInformationQuery> queries) {
        return Optional.of(queries.stream()
                        .map(REFGroceryItemInformationQuery::groceryItemId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .filter(idList -> !idList.isEmpty())
                .map(refGroceryItemRepository::findAllSummarizedDTOsByIds)
                .map(refGroceryItemInformationMapper::toSummaryResultList)
                .orElseGet(List::of);
    }

    /**
     * 배치 조회 시 N+1 방지를 위한 최적화된 메서드
     * 1. GroceryItem 배치 조회 (1 query)
     * 2. MinorCategory 배치 조회 (1 query)
     * 3. 메모리에서 조합
     * 총 2개의 쿼리로 해결!
     */
    @Transactional(readOnly = true)
    public List<REFGroceryItemDetailInfoResult> getGroceryItemDetailOverViewInformationList(List<REFGroceryItemInformationQuery> queries) {
        // 1. GroceryItem ID 추출
        List<Long> groceryItemIds = queries.stream()
                .map(REFGroceryItemInformationQuery::groceryItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (groceryItemIds.isEmpty()) {
            return List.of();
        }

        // 2. GroceryItem 배치 조회
        List<REFGroceryItemDetailDTO> groceryItems = refGroceryItemRepository
                .findAllDetailDTOsByIds(groceryItemIds);

        // 3. MinorCategory ID 추출
        List<Long> minorCategoryIds = groceryItems.stream()
                .map(REFGroceryItemDetailDTO::minorCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 4. MinorCategory 배치 조회 (N+1 방지!)
        Map<Long, String> categoryNameMap = refMinorGroceryCategoryRepository
                .findAllById(minorCategoryIds)
                .stream()
                .collect(Collectors.toMap(
                        REFMinorGroceryCategory::getId,
                        REFMinorGroceryCategory::formatFullCategoryPath
                ));

        // 5. 메모리에서 조합
        return groceryItems.stream()
                .map(dto -> {
                    REFGroceryItemDetailInfoResult result = refGroceryItemInformationMapper.toDetailResult(dto);
                    String categoryFullName = categoryNameMap.getOrDefault(dto.minorCategoryId(), "");
                    return enrichWithCategoryName(result, categoryFullName);
                })
                .toList();
    }

    private REFGroceryItemDetailInfoResult mappingResultFromDto(REFGroceryItemDetailDTO dto) {
        REFGroceryItemDetailInfoResult result = refGroceryItemInformationMapper.toDetailResult(dto);

        String categoryFullName = refGroceryItemCategoricalService
                .getCategoryFullNameWithSeparator(dto.minorCategoryId())
                .orElse("");

        return enrichWithCategoryName(result, categoryFullName);
    }

    /**
     * Result에 카테고리 이름 추가
     * Record라면 새로운 인스턴스 생성 필요
     */
    private REFGroceryItemDetailInfoResult enrichWithCategoryName(
            REFGroceryItemDetailInfoResult original,
            String categoryFullName) {
        return new REFGroceryItemDetailInfoResult(
                original.groceryItemId(),
                original.minorCategoryId(),
                original.majorCategoryId(),
                original.groceryItemName(),
                categoryFullName,  // 여기서 채움!
                original.representativeImageUrl(),
                original.groceryItemClassificationCode(),
                original.categoryLabelColorCode()
        );

    }
}
