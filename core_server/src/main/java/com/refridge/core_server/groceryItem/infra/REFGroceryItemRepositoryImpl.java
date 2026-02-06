package com.refridge.core_server.groceryItem.infra;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class REFGroceryItemRepositoryImpl implements REFGroceryItemRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

}
