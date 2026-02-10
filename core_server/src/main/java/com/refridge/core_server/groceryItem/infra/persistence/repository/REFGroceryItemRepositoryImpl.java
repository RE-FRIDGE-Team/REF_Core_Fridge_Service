package com.refridge.core_server.groceryItem.infra.persistence.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepositoryCustom;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemDetailDTO;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemSummarizedDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class REFGroceryItemRepositoryImpl implements REFGroceryItemRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<REFGroceryItemSummarizedDTO> findSummarizedDTOById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<REFGroceryItemSummarizedDTO> findAllSummarizedDTOsByIds(List<Long> ids) {
        return List.of();
    }


    @Override
    public Optional<REFGroceryItemDetailDTO> findDetailDTOById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<REFGroceryItemDetailDTO> findAllDetailDTOsByIds(List<Long> ids) {
        return List.of();
    }

}
