package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 제품명이 변경된 부정 피드백을 처리합니다.
 *
 * 처리 흐름:
 *   1. Redis Hash에 "원본 -> 수정본" 선택 횟수 누적
 *   2. 전체 선택 횟수(파이프라인 선택 포함) 대비 비율 계산
 *   3. 확정 조건 충족 시 REFAliasConfirmationService.confirmAlias() 호출
 *      -> DB CONFIRMED 저장 + Redis alias:confirmed Hash 캐싱
 *
 * Redis 구조:
 *   Hash Key : feedback:product-alias:{originalName}
 *   Field    : {correctedName}   (수정본별 선택 횟수)
 *   Field    : __total__         (전체 선택 횟수 - 파이프라인 포함)
 *
 * 확정 조건 (REFAliasConfirmationService):
 *   수정본 선택 횟수 >= MIN_ALIAS_COUNT(10)
 *   AND 수정본 선택 비율 >= MIN_ALIAS_RATIO(0.7)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameAliasHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFAliasConfirmationService aliasConfirmationService;

    private static final String ALIAS_CANDIDATE_PREFIX = "feedback:product-alias:";

    /** 전체 선택 횟수를 저장하는 Hash field 이름 */
    private static final String TOTAL_FIELD = "__total__";

    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.PRODUCT_NAME;
    }

    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String originalName = event.snapshot().getProductName();
        String correctedName = event.correction().getCorrectedProductName();

        if (originalName == null || correctedName == null) return;
        if (originalName.equals(correctedName)) return;

        String hashKey = ALIAS_CANDIDATE_PREFIX + originalName;

        // 수정본 선택 횟수 증가
        Long occurrenceCount = redisTemplate.opsForHash()
                .increment(hashKey, correctedName, 1);

        // 전체 선택 횟수 증가 (수정본 선택도 전체에 포함)
        Long totalCount = redisTemplate.opsForHash()
                .increment(hashKey, TOTAL_FIELD, 1);

        if (occurrenceCount == null || totalCount == null) {
            log.warn("[Alias 핸들러] Redis increment 실패. originalName='{}'", originalName);
            return;
        }

        log.info("[Alias 핸들러] 매핑 누적. '{}' -> '{}', 횟수={}/{}, feedbackId={}",
                originalName, correctedName, occurrenceCount, totalCount,
                event.feedbackId().getValue());

        // 확정 조건 검사
        if (aliasConfirmationService.meetsConfirmationThreshold(occurrenceCount, totalCount)) {
            aliasConfirmationService.confirmAlias(
                    originalName, correctedName, occurrenceCount, totalCount);

            log.info("[Alias 핸들러] alias 확정 완료. '{}' -> '{}'", originalName, correctedName);
        }
    }
}