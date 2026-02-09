package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.query.REFGroceryItemInformationQuery;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemDetailInfoResult;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemSummaryInfoResult;
import com.refridge.core_server.groceryItem.application.mapper.REFGroceryItemInformationMapper;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class REFGroceryItemOverViewInformationService {

    private final REFGroceryItemRepository refGroceryItemRepository;

    private final REFGroceryItemCategoryValidatorService categoryValidatorService;

    private final REFGroceryItemInformationMapper refGroceryItemInformationMapper;

    /**
     * 식재료 필수 요약 정보를 조회 후 획득할 수 있다. (식재료 ID 기준)
     * 필수 정보 : 식재료 ID, 대표 이미지(썸네일), 식재료 이름
     * @param query 식재료 필수 요약 정보 서비스 레벨 요청 쿼리
     * @return REFGroceryItemSummaryInfoResult 식재료 요약 정보 응답 결과
     */
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
                .map(refGroceryItemInformationMapper::toDetailResult)
                .orElseThrow(() -> new IllegalStateException("Grocery Item 요약 정보 조회에 실패했습니다."));

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

    @Transactional(readOnly = true)
    public List<REFGroceryItemDetailInfoResult> getGroceryItemDetailOverViewInformationList(List<REFGroceryItemInformationQuery> queries) {
        return Optional.of(queries.stream()
                .map(REFGroceryItemInformationQuery::groceryItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
                .filter(idList -> !idList.isEmpty())
                .map(refGroceryItemRepository::findAllDetailDTOsByIds)
                .map(refGroceryItemInformationMapper::toDetailResultList)
                .orElseGet(List::of);
    }
}
