package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import com.refridge.core_server.recognition_feedback.infra.brand.REFBrandDictionaryFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 브랜드명이 변경된 부정 피드백을 처리합니다.
 *
 * <h3>Redis 카운터 만료 후 복원 전략</h3>
 * increment() 결과가 1이면 키가 새로 생성된 것 = TTL 만료 후 첫 접근.
 * 이 경우 DB에서 실제 누적 횟수를 조회하여 Redis 카운터를 복원합니다.
 * 이후 increment는 복원된 값 기준으로 정확하게 누적됩니다.
 *
 * Redis가 살아있는 경우에는 DB 조회 없이 카운터만 증가합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFBrandImprovementHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFBrandDictionaryFlushService brandDictionaryFlushService;
    private final REFRecognitionFeedbackRepository feedbackRepository;

    private static final String BRAND_COUNTER_PREFIX = "feedback:brand:";
    public static final int MIN_BRAND_COUNT = 2;
    private static final int BATCH_SIZE = 20;
    private static final Duration COUNTER_TTL = Duration.ofDays(30);

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.BRAND;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String correctedBrand = event.correction().getCorrectedBrandName();
        if (correctedBrand == null || correctedBrand.isBlank()) return;

        String counterKey = BRAND_COUNTER_PREFIX + correctedBrand;

        // ── Step 1: 카운터 증가 ───────────────────────────────────
        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count == null) {
            log.warn("[브랜드 핸들러] Redis increment 실패. brand='{}'", correctedBrand);
            return;
        }

        // ── Step 2: Redis miss 복원 ───────────────────────────────
        // increment 결과가 1 = 키가 새로 생성됨 = TTL 만료 후 첫 접근
        // DB에서 실제 누적 횟수 조회 후 Redis 복원
        if (count == 1) {
            long dbCount = feedbackRepository.countByCorrectBrandName(correctedBrand);
            if (dbCount > 1) {
                // SET으로 덮어쓰기 (이미 increment로 1이 세팅된 상태)
                // dbCount가 실제 전체 횟수이므로 그대로 세팅
                redisTemplate.opsForValue().set(counterKey, String.valueOf(dbCount));
                count = dbCount;
                log.info("[브랜드 핸들러] Redis 복원. brand='{}', dbCount={}", correctedBrand, dbCount);
            }
        }

        // ── Step 3: TTL 갱신 ──────────────────────────────────────
        redisTemplate.expire(counterKey, COUNTER_TTL);

        log.info("[브랜드 핸들러] brand='{}', count={}, feedbackId={}",
                correctedBrand, count, event.feedbackId().getValue());

        if (count < MIN_BRAND_COUNT) return;

        // ── Step 4: PENDING 추가 ──────────────────────────────────
        Long pendingSize = brandDictionaryFlushService.addToPending(correctedBrand);
        log.info("[브랜드 핸들러] PENDING 추가. brand='{}', pendingSize={}", correctedBrand, pendingSize);

        // ── Step 5: BATCH_SIZE 즉시 flush ─────────────────────────
        if (pendingSize != null && pendingSize >= BATCH_SIZE) {
            log.info("[브랜드 핸들러] BATCH_SIZE({}) 도달, 즉시 flush.", BATCH_SIZE);
            brandDictionaryFlushService.flush();
        }
    }
}