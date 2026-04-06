package com.refridge.core_server.recognition_feedback.infra.batch;

import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.refridge.core_server.recognition_feedback.domain.review.REFReviewStatus.APPROVED;
import static com.refridge.core_server.recognition_feedback.domain.review.REFReviewStatus.REJECTED;
import static com.refridge.core_server.recognition_feedback.infra.batch.REFReviewItemCleanupJobConfig.IMMEDIATE_CLEANUP_THRESHOLD;

/**
 * 검수 항목 일괄 삭제 잡 스케줄러입니다.
 *
 * <h3>Spring Batch 6 JobLauncher → JobOperator 교체</h3>
 * <pre>
 *   // deprecated (6.0, removal in 6.2+)
 *   JobLauncher.run(job, params)
 *
 *   // 교체 API
 *   JobOperator.start(job, params)  ← org.springframework.batch.core.launch.JobOperator
 * </pre>
 *
 * <p>
 * {@code JobOperator.start(Job, JobParameters)}는 Job에 {@code JobParametersIncrementer}가
 * 설정되어 있으면 전달된 params를 <b>무시</b>하고 {@code startNextInstance(job)}로 위임합니다.
 * (Batch 6.0 GitHub Issue #5230)<br>
 * 따라서 {@link REFReviewItemCleanupJobConfig}의 JobBuilder에서
 * {@code .incrementer(new RunIdIncrementer())}를 <b>제거</b>하고,
 * 고유성은 이 스케줄러가 직접 {@code run.id = currentTimeMillis} 파라미터로 보장합니다.
 * </p>
 *
 * <h3>실행 트리거</h3>
 * <ul>
 *   <li><b>정기 실행</b>: 매일 새벽 2시</li>
 *   <li><b>즉시 실행</b>: 터미널 항목이 {@link REFReviewItemCleanupJobConfig#IMMEDIATE_CLEANUP_THRESHOLD}
 *       (3,000건) 초과 시 — 매 10분 폴링</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFReviewItemCleanupJobConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFReviewItemCleanupScheduler {

    /**
     * Batch 6: JobLauncher 대신 JobOperator 주입.
     * Spring Boot 4 BatchAutoConfiguration이 TaskExecutorJobOperator를 자동 등록합니다.
     */
    private final JobOperator jobOperator;

    @Qualifier("refReviewItemCleanupJob")
    private final Job refReviewItemCleanupJob;

    private final REFFeedbackReviewItemRepository reviewItemRepository;

    /* ──────────────────── Scheduled ──────────────────── */

    /**
     * 매일 새벽 2시 정기 실행.
     * 삭제 대상이 없으면 Reader가 빈 리스트를 반환하여 즉시 완료됩니다.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledCleanup() {
        log.info("[Cleanup 스케줄러] 정기 실행 시작.");
        launchJob("scheduled");
    }

    /**
     * 매 10분마다 터미널 항목 수를 확인하여 임계값 초과 시 즉시 실행합니다.
     */
    @Scheduled(fixedDelay = 600_000)
    public void checkAndCleanupIfNeeded() {
        long terminalCount = reviewItemRepository.countByStatus(APPROVED)
                + reviewItemRepository.countByStatus(REJECTED);

        if (terminalCount >= IMMEDIATE_CLEANUP_THRESHOLD) {
            log.info("[Cleanup 스케줄러] 임계값 초과({}/{}건), 즉시 실행.",
                    terminalCount, IMMEDIATE_CLEANUP_THRESHOLD);
            launchJob("immediate");
        } else {
            log.debug("[Cleanup 스케줄러] 임계값 미달({}/{}건), 스킵.",
                    terminalCount, IMMEDIATE_CLEANUP_THRESHOLD);
        }
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    /**
     * Spring Batch 잡을 실행합니다.
     *
     * <p>
     * Batch 6 신규 API: {@code JobOperator.start(Job, JobParameters)}<br>
     * {@code run.id}에 현재 시각을 넣어 매 실행마다 고유한 JobParameters를 보장합니다.
     * (RunIdIncrementer 없이 고유성 확보)
     * </p>
     *
     * @param trigger 실행 트리거 구분 (로깅용: "scheduled" / "immediate")
     */
    private void launchJob(String trigger) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .addString("trigger", trigger)
                    .toJobParameters();

            jobOperator.start(refReviewItemCleanupJob, params);
            log.info("[Cleanup 스케줄러] 잡 실행 완료. trigger={}", trigger);

        } catch (Exception e) {
            log.error("[Cleanup 스케줄러] 잡 실행 실패. trigger={}, 사유: {}",
                    trigger, e.getMessage());
        }
    }
}