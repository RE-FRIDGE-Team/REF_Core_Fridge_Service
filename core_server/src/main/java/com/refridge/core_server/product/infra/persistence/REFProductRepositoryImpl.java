package com.refridge.core_server.product.infra.persistence;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.product.domain.REFProductRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class REFProductRepositoryImpl implements REFProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;


}
