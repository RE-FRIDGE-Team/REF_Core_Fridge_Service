package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 제품명이 변경된 부정 피드백을 처리합니다.
 * <p>
 * 처리 로직:
 * <ol>
 *   <li>{@code 원본 정제명 → 사용자 수정명} 매핑을 Redis Hash에 저장</li>
 *   <li>동일 매핑이 K회 누적되면 제품명 alias로 확정</li>
 *   <li>확정된 alias는 인식 파이프라인의 파서 이후 변환 단계에서 활용 가능</li>
 * </ol>
 * <p>
 * Redis 구조:<pre>
 *   Hash: feedback:product-alias:{originalProductName}
 *   Field: {correctedProductName}
 *   Value: 누적 횟수
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameAliasHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;

    private static final String ALIAS_KEY_PREFIX = "feedback:product-alias:";

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

        String hashKey = ALIAS_KEY_PREFIX + originalName;
        redisTemplate.opsForHash().increment(hashKey, correctedName, 1);

        log.info("[제품명 alias] 매핑 누적. '{}' → '{}', feedbackId={}",
                originalName, correctedName, event.feedbackId().getValue());
    }
}