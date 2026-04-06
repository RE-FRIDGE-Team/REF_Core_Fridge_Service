package com.refridge.core_server.recognition_feedback.infra.batch;

import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * 검수 항목 일괄 삭제 Spring Batch 잡 설정 클래스입니다.
 *
 * <h3>Spring Boot 4.0.x / Spring Batch 6.0.x 기준 패키지 변경 목록</h3>
 * <pre>
 *   Job              → org.springframework.batch.core.job.Job
 *   Step             → org.springframework.batch.core.step.Step
 *   ItemWriter       → org.springframework.batch.infrastructure.item.ItemWriter
 *   ListItemReader   → org.springframework.batch.infrastructure.item.support.ListItemReader
 *   JobBuilder       → org.springframework.batch.core.job.builder.JobBuilder        (유지)
 *   StepBuilder      → org.springframework.batch.core.step.builder.StepBuilder      (유지)
 *   JobRepository    → org.springframework.batch.core.repository.JobRepository      (유지)
 * </pre>
 *
 * <h3>⚠️ RunIdIncrementer 제거 이유</h3>
 * <p>
 * {@code JobOperator.start(Job, JobParameters)}는 Job에 {@code JobParametersIncrementer}가
 * 설정되어 있으면 전달된 params를 <b>무시</b>하고 {@code startNextInstance(job)}으로 위임합니다.
 * (Batch 6.0 GitHub Issue #5230)<br>
 * 고유성 보장은 스케줄러가 직접 {@code run.id = currentTimeMillis} 파라미터로 처리합니다.
 * </p>
 *
 * <h3>Batch 6 chunk API 변경</h3>
 * <pre>
 *   // Batch 5 — deprecated in 6
 *   .chunk(500, transactionManager)
 *
 *   // Batch 6 정식 API
 *   .chunk(500)
 *   .transactionManager(transactionManager)
 * </pre>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFReviewItemCleanupScheduler
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class REFReviewItemCleanupJobConfig {

    private final REFFeedbackReviewItemRepository reviewItemRepository;

    // Spring Boot 4 BatchAutoConfiguration이 자동 등록
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    /** 청크 단위 — 한 트랜잭션으로 묶이는 DELETE 건수 */
    static final int CHUNK_SIZE = 500;

    /** 잡 1회 실행당 최대 삭제 건수. 초과분은 다음 스케줄에서 처리 */
    static final int MAX_DELETE_PER_RUN = 5_000;

    /**
     * 터미널 항목(APPROVED/REJECTED) 수가 이 값을 초과하면
     * 스케줄러가 즉시 잡 실행을 트리거합니다.
     *
     * @see REFReviewItemCleanupScheduler#checkAndCleanupIfNeeded()
     */
    public static final int IMMEDIATE_CLEANUP_THRESHOLD = 3_000;

    /* ──────────────────── Job ──────────────────── */

    /**
     * 검수 항목 일괄 삭제 잡.
     *
     * <p>
     * ⚠️ RunIdIncrementer 미사용: {@code JobOperator.start(Job, JobParameters)} 사용 시
     * incrementer가 설정된 Job은 params를 무시하는 버그(Issue #5230)가 있습니다.
     * 고유성은 스케줄러의 {@code run.id = currentTimeMillis} 파라미터로 보장합니다.
     * </p>
     */
    @Bean
    public Job refReviewItemCleanupJob() {
        return new JobBuilder("REFReviewItemCleanupJob", jobRepository)
                // RunIdIncrementer 제거 — JobOperator.start() 와 incrementer 조합 시 params 무시 버그
                .start(refReviewItemCleanupStep())
                .build();
    }

    /* ──────────────────── Step ──────────────────── */

    @Bean
    public Step refReviewItemCleanupStep() {
        return new StepBuilder("REFReviewItemCleanupStep", jobRepository)
                .<Long, Long>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(reviewItemCleanupReader())
                .writer(reviewItemCleanupWriter())
                .build();
    }

    /* ──────────────────── Reader ──────────────────── */

    /**
     * 터미널 상태(APPROVED/REJECTED) 항목의 ID 목록을 조회하는 Reader.
     *
     * <p>
     * ⚠️ {@code @Bean} 미등록: 잡 실행마다 새로 생성되어야 하는 상태 객체입니다.
     * 싱글톤으로 등록하면 두 번째 실행 시 이미 소진된 Reader를 재사용하여 아무것도 삭제되지 않습니다.
     * </p>
     */
    private ListItemReader<Long> reviewItemCleanupReader() {
        List<Long> ids = reviewItemRepository.findTerminalItemIdsForCleanup(MAX_DELETE_PER_RUN);
        log.info("[Cleanup Job Reader] 삭제 대상 조회 완료. 건수={}", ids.size());
        return new ListItemReader<>(ids);
    }

    /* ──────────────────── Writer ──────────────────── */

    @Bean
    public ItemWriter<Long> reviewItemCleanupWriter() {
        return chunk -> {
            // Batch 6: chunk.getItems() → List<? extends Long>
            // deleteAllById(Iterable<? extends ID>) 이므로 직접 전달
            if (chunk.isEmpty()) return;
            reviewItemRepository.deleteAllById(chunk.getItems());
            log.info("[Cleanup Job Writer] 삭제 완료. 건수={}", chunk.size());
        };
    }
}