package com.refridge.core_server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * GroceryItem 도메인의 비동기 처리 전용 ThreadPoolTaskExecutor를 정의합니다.
 *
 * <h3>역할</h3>
 * <p>
 * 신규 GroceryItem 생성 시 메타데이터를 자동으로 채우는
 * {@code REFGroceryItemMetadataAutoFillHandler}의 동시 실행 수를 제한하여
 * DB 커넥션 풀과 카테고리 lookup 부하를 제어합니다.
 * </p>
 *
 * <h3>설계 의도 — Bulk 안전성</h3>
 * <p>
 * {@code LifeCycleService.createGroceryItemBulk()}로 1,000건 생성 시
 * 1,000개 이벤트가 발행되지만, 이 Executor가 동시 4개로 제한하여 큐잉됩니다.
 * 따라서 별도 Bulk 분기 처리 없이 모든 생성 경로(단건/Bulk/upsert)가
 * 동일한 이벤트 메커니즘으로 처리됩니다.
 * </p>
 *
 * <h3>{@code @EnableAsync}</h3>
 * <p>
 * Spring Boot 4의 자동 설정에 의해 활성화되지 않은 경우를 대비해 명시적으로 활성화합니다.
 * 이미 다른 @Configuration에서 활성화되어 있다면 중복 활성화는 무해합니다.
 * </p>
 *
 * <h3>운영 튜닝 가이드</h3>
 * <ul>
 *   <li>HikariCP 풀 크기(현재 20)를 초과하지 않도록 corePoolSize 조정</li>
 *   <li>queueCapacity는 Bulk 최대 크기보다 크게 (1,000건 Bulk 대비 2,000 권장)</li>
 *   <li>rejectionPolicy=CallerRunsPolicy — 큐가 가득 차면 호출 스레드에서 직접 실행
 *       (실행 누락 방지)</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 14.
 */
@Slf4j
@Configuration
@EnableAsync
public class REFGroceryItemAsyncConfig {

    public static final String METADATA_AUTO_FILL_EXECUTOR = "metadataAutoFillExecutor";

    @Bean(name = METADATA_AUTO_FILL_EXECUTOR)
    public Executor metadataAutoFillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("metadata-autofill-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("[REFGroceryItemAsyncConfig] metadataAutoFillExecutor 초기화 완료. " +
                "corePoolSize=4, maxPoolSize=8, queueCapacity=2000");
        return executor;
    }
}