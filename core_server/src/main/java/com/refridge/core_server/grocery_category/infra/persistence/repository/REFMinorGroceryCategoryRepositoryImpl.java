package com.refridge.core_server.grocery_category.infra.persistence.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class REFMinorGroceryCategoryRepositoryImpl {

    private final JPAQueryFactory jpaQueryFactory;

}
