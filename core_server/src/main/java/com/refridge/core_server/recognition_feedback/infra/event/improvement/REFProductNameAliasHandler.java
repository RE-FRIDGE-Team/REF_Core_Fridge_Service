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

        // TODO : Redis에 해당 키로 횟수를 늘림, 해당 originalName에 대해 사용자가 이전과 동일한 인식을
        //  추정한 경우, 사용자에게 original vs corrected 선택지를 보여주고,
        //  corrected가 선택된 경우에 계속해서 횟수를 늘림. 카운트가 일정이상 늘어나면 redis로부터 신호를 받아
        //  제품명 pipeline 사전을 새로 만들어 해당 사전을 앞단에 놓아 제품명 정제 결과를 alias로 변환하는 로직을 추가
        //  해당 로직을 통해 추가적인 정규표현식 개발 없이도 제품명 인식 개선 효과를 기대할 수 있음.
        String hashKey = ALIAS_KEY_PREFIX + originalName;
        Long count = redisTemplate.opsForHash().increment(hashKey, correctedName, 1);

        log.info("[제품명 alias] 매핑 누적. '{}' → '{}', feedbackId={}",
                originalName, correctedName, event.feedbackId().getValue());

        // TODO : count 비교, 특정 threshold 이상이면 alias 확정 처리
        //  (예: 별도의 Redis Set에 확정된 alias DB에 영구 저장, 그 전에 인식 파이프라인의 정제 제품명 alias 사전을 만들어야 함)


    }
}