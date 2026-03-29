package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewType;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 브랜드명이 변경된 부정 피드백을 처리합니다.
 * <p>
 * 처리 로직:
 * <ol>
 *   <li>수정된 브랜드명에 대해 Redis 카운터 증가</li>
 *   <li>임계값 미도달 → 검수 큐에 적재 (중복이면 누적 카운트 증가)</li>
 *   <li>임계값 도달 → 검수 큐에 적재 (관리자가 최종 승인 시 사전에 추가)</li>
 * </ol>
 * 브랜드 사전은 잘못 추가하면 파싱에 영향을 주므로 자동 추가하지 않고 관리자 검수를 거칩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFBrandImprovementHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFFeedbackReviewItemRepository reviewItemRepository;

    private static final String COUNTER_KEY_PREFIX = "feedback:brand:";

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.BRAND;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String correctedBrand = event.correction().getCorrectedBrandName();
        if (correctedBrand == null || correctedBrand.isBlank()) return;

        // Redis 카운터 증가
        String counterKey = COUNTER_KEY_PREFIX + correctedBrand;
        redisTemplate.opsForValue().increment(counterKey);

        // 검수 큐에 적재 (중복이면 누적 카운트만 증가)
        reviewItemRepository.findByReviewTypeAndTargetValue(REFReviewType.BRAND_ADDITION, correctedBrand)
                .ifPresentOrElse(
                        REFFeedbackReviewItem::incrementOccurrence,
                        () -> reviewItemRepository.save(
                                REFFeedbackReviewItem.create(
                                        REFReviewType.BRAND_ADDITION,
                                        correctedBrand,
                                        buildContext(event),
                                        event.feedbackId().getValue()
                                )
                        )
                );

        log.info("[브랜드 보강] 검수 큐 적재. brand='{}', feedbackId={}",
                correctedBrand, event.feedbackId().getValue());
    }

    private String buildContext(REFNegativeFeedbackEvent event) {
        return String.format("원본제품명='%s', 원본브랜드='%s', completedBy='%s'",
                event.snapshot().getProductName(),
                event.snapshot().getBrandName(),
                event.snapshot().getCompletedBy());
    }
}