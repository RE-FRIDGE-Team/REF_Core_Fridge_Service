package com.refridge.core_server.grocery_category.application.mapper;

import com.refridge.core_server.grocery_category.application.dto.result.REFCategoryHierarchyDataResult;
import com.refridge.core_server.grocery_category.domain.vo.REFCategoryColorTag;
import com.refridge.core_server.grocery_category.infra.persistence.dto.REFCategoryMetaDataWithCountRowDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link REFCategoryMetaDataWithCountRowDto} 리스트를 {@link REFCategoryHierarchyDataResult}로 변환하는 매퍼 클래스입니다.<p>
 * infra 레이어의 DTO를 application 레이어의 결과 DTO로 변환하는 역할을 담당합니다.<p>
 */
@Component
public class REFCategoryHierarchyResultMapper {

    public REFCategoryHierarchyDataResult toHierarchyResult(List<REFCategoryMetaDataWithCountRowDto> rows) {

        // majorCategoryId 기준으로 그룹핑
        Map<Long, List<REFCategoryMetaDataWithCountRowDto>> groupedByMajor = rows.stream()
                .collect(Collectors.groupingBy(
                        REFCategoryMetaDataWithCountRowDto::majorCategoryId,
                        Collectors.toList()
                ));

        List<REFCategoryHierarchyDataResult.MajorCategoryWithCount> majorCategories = groupedByMajor.entrySet().stream()
                .map(entry -> {
                    Long majorId = entry.getKey();
                    List<REFCategoryMetaDataWithCountRowDto> minorRows = entry.getValue();

                    // 첫 번째 row에서 대분류 이름 추출
                    String majorName = minorRows.getFirst().majorCategoryName();

                    // 중분류 리스트 생성 (minorCategoryId가 null이면 중분류 없는 대분류)
                    List<REFCategoryHierarchyDataResult.MinorCategoryWithCount> minorCategories = minorRows.stream()
                            .filter(row -> row.minorCategoryId() != null)
                            .map(row -> REFCategoryHierarchyDataResult.MinorCategoryWithCount.builder()
                                    .minorCategoryId(row.minorCategoryId())
                                    .minorCategoryName(row.minorCategoryName())
                                    .itemCount(row.itemCount())
                                    .build())
                            .toList();

                    long totalItemCount = minorCategories.stream()
                            .mapToLong(REFCategoryHierarchyDataResult.MinorCategoryWithCount::itemCount)
                            .sum();

                    return REFCategoryHierarchyDataResult.MajorCategoryWithCount.builder()
                            .majorCategoryId(majorId)
                            .majorCategoryName(majorName)
                            .colorTagHexCode(REFCategoryColorTag.fromMajorCategoryId(majorId).getHexCode())
                            .totalItemCount(totalItemCount)
                            .minorCategories(minorCategories)
                            .build();
                })
                .sorted((a, b) -> Long.compare(a.majorCategoryId(), b.majorCategoryId()))
                .toList();

        return REFCategoryHierarchyDataResult.builder()
                .majorCategories(majorCategories)
                .build();
    }}
