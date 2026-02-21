package com.refridge.core_server.common;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/***
 * {@code REFQueryDslConfig}는 {@code QueryDSL}을 사용하기 위한 설정 클래스입니다.<p>
 * {@code JPAQueryFactory} 빈을 생성하여, 애플리케이션 전반에서 {@code QueryDSL}울 활용한 쿼리 작성을 가능하게 합니다.<p>
 * <pre>
 * {@code
 * @Repository
 * @RequiredArgsConstructor
 * public class REFGroceryItemRepositoryImpl implements REFGroceryItemRepositoryCustom {
 *
 *      // 생성자 주입 방식으로 JPAQueryFactory 사용
 *      private final JPAQueryFactory jpaQueryFactory;
 *      ....
 * }
 * </pre>
 *
 */
@Configuration
public class REFQueryDslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }

}