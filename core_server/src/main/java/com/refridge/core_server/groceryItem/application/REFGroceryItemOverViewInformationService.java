package com.refridge.core_server.groceryItem.application;

import com.refridge.core_server.groceryItem.application.dto.query.REFGroceryItemSummaryInfoQuery;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemSummaryInfoResult;
import com.refridge.core_server.groceryItem.application.mapper.REFGroceryItemSummaryInfoMapper;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidatorService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class REFGroceryItemOverViewInformationService {

    private final REFGroceryItemRepository refGroceryItemRepository;

    private final REFGroceryItemCategoryValidatorService categoryValidatorService;

    private final REFGroceryItemSummaryInfoMapper refGroceryItemSummaryInfoMapper;

    /**
     * 식재료 필수 요약 정보를 조회 후 획득할 수 있다. (식재료 ID 기준)
     * 필수 정보 : 식재료 ID, 대표 이미지(썸네일), 식재료 이름
     * @param query 식재료 필수 요약 정보 서비스 레벨 요청 쿼리
     * @return REFGroceryItemSummaryInfoResult 식재료 요약 정보 응답 결과
     */
    @Transactional(readOnly = true)
    public REFGroceryItemSummaryInfoResult getGroceryItemSummaryOverViewInformation(REFGroceryItemSummaryInfoQuery query) {
        return Optional.ofNullable(query)
                .filter(q -> q.groceryItemId() != null)
                .map(REFGroceryItemSummaryInfoQuery::groceryItemId)
                .flatMap(refGroceryItemRepository::findSummarizedDTOById)
                .map(refGroceryItemSummaryInfoMapper::toResult)
                .orElseThrow(() -> new IllegalStateException("Grocery Item 요약 정보 조회에 실패했습니다."));
    }

    public
}
