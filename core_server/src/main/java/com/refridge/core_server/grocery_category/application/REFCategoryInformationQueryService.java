package com.refridge.core_server.grocery_category.application;

import com.refridge.core_server.grocery_category.application.dto.result.REFCategoryHierarchyDataResult;
import com.refridge.core_server.grocery_category.application.mapper.REFCategoryHierarchyResultMapper;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class REFCategoryInformationQueryService {

    private final REFMajorGroceryCategoryRepository majorGroceryCategoryRepository;

    private final REFCategoryHierarchyResultMapper categoryHierarchyResultMapper;

    /**
     * 카테고리 계층 구조 메타데이터를 조회합니다.
     * @return REFCategoryHierarchyDataResult
     */
    @Transactional(readOnly = true)
    public REFCategoryHierarchyDataResult getCategoryHierarchyMetaData() {
        return categoryHierarchyResultMapper.toHierarchyResult(
                majorGroceryCategoryRepository.findAllCategoryHierarchyWithItemCount());
    }

}