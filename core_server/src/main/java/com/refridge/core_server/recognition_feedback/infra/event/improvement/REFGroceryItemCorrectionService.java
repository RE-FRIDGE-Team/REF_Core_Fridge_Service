package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.grocery_item_correction.domain.REFGroceryItemNameCorrection;
import com.refridge.core_server.grocery_item_correction.domain.REFGroceryItemNameCorrectionRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFGroceryItemCorrectionConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 식재료명 교정 확정 서비스입니다.
 *
 * <h3>변경 사항 (2026. 4. 14.)</h3>
 * <p>
 * {@code confirmCorrection()}에 {@code originalProductName} 파라미터가 추가되었습니다.
 * 이 값은 {@link REFGroceryItemCorrectionConfirmedEvent}에 포함되어
 * Product BC에서 실제 제품명으로 Product를 upsert하는 데 사용됩니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 8. (수정: 2026. 4. 14.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFGroceryItemCorrectionService {

    private final StringRedisTemplate redisTemplate;
    private final REFGroceryItemNameCorrectionRepository correctionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public static final int MIN_CORRECTION_COUNT = 10;
    public static final double MIN_CORRECTION_RATIO = 0.7;
    public static final double DOMINANCE_RATIO = 3.0;

    public static final String CORRECTION_CANDIDATE_PREFIX = "feedback:grocery-item-correction:";
    public static final String CORRECTION_CONFIRMED_KEY = "grocery-item-correction:confirmed";
    public static final String TOTAL_FIELD = "__total__";

    public boolean meetsConfirmationThreshold(String candidateKey, long occurrenceCount,
                                              long totalCount, Map<String, Long> allCounts) {
        if (occurrenceCount < MIN_CORRECTION_COUNT) return false;
        if (totalCount == 0) return false;
        double ratio = (double) occurrenceCount / totalCount;
        if (ratio < MIN_CORRECTION_RATIO) return false;

        OptionalLong secondMax = allCounts.entrySet().stream()
                .filter(e -> !e.getKey().equals(candidateKey))
                .filter(e -> !e.getKey().equals(TOTAL_FIELD))
                .mapToLong(Map.Entry::getValue)
                .max();

        if (secondMax.isPresent() && secondMax.getAsLong() > 0) {
            double dominance = (double) occurrenceCount / secondMax.getAsLong();
            if (dominance < DOMINANCE_RATIO) return false;
        }
        return true;
    }

    public Map<String, Long> getAllCandidateCounts(String hashKey) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(hashKey);
        Map<String, Long> result = new HashMap<>();
        raw.forEach((k, v) -> {
            try {
                result.put(k.toString(), Long.parseLong(v.toString()));
            } catch (NumberFormatException ignored) {}
        });
        return result;
    }

    /**
     * 식재료명 교정을 확정하고 DB, Redis, 이벤트 발행을 처리합니다.
     *
     * <h3>originalProductName 용도</h3>
     * <p>
     * Product BC가 {@code productName}에 실제 제품명을 사용하여 Product를 upsert합니다.
     * 이렇게 해야 다음 인식 시 {@code ProductIndexSearch}가 실제 제품명으로 정확히 매칭합니다.
     * null이면 GroceryItem BC에서 {@code originalName}(식재료명)을 폴백으로 사용합니다.
     * </p>
     *
     * @param originalName        원본 식재료명 (집계 키, 로깅용)
     * @param correctedName       확정할 교정 식재료명
     * @param occurrenceCount     선택 횟수
     * @param totalCount          전체 반응 횟수
     * @param originalProductName 원본 실제 제품명 (nullable)
     */
    @Transactional
    public void confirmCorrection(String originalName, String correctedName,
                                  long occurrenceCount, long totalCount,
                                  String originalProductName) {

        REFGroceryItemNameCorrection correction =
                correctionRepository.findByOriginalName(originalName)
                        .map(existing -> {
                            existing.updateCounts(occurrenceCount, totalCount);
                            if (!existing.isConfirmed()) existing.confirm();
                            return existing;
                        })
                        .orElseGet(() -> {
                            REFGroceryItemNameCorrection newCorrection =
                                    REFGroceryItemNameCorrection.createCandidate(
                                            originalName, correctedName,
                                            occurrenceCount, totalCount);
                            newCorrection.confirm();
                            return newCorrection;
                        });

        correctionRepository.save(correction);

        redisTemplate.opsForHash().put(CORRECTION_CONFIRMED_KEY, originalName, correctedName);

        eventPublisher.publishEvent(new REFGroceryItemCorrectionConfirmedEvent(
                originalName, correctedName, occurrenceCount, totalCount,
                originalProductName));

        log.info("[식재료명 교정 확정] '{}' → '{}', 제품명='{}', 횟수={}/{}, 비율={:.1f}%",
                originalName, correctedName, originalProductName,
                occurrenceCount, totalCount,
                (double) occurrenceCount / totalCount * 100);
    }

    @Transactional
    public void reopenCorrection(String originalName) {
        correctionRepository.findByOriginalName(originalName)
                .filter(REFGroceryItemNameCorrection::isConfirmed)
                .ifPresent(correction -> {
                    correction.reopen();
                    correctionRepository.save(correction);
                    log.info("[식재료명 교정 재심사] '{}' CONFIRMED → CANDIDATE 전환", originalName);
                });

        redisTemplate.opsForHash().delete(CORRECTION_CONFIRMED_KEY, originalName);
        log.info("[식재료명 교정 재심사] Redis에서 '{}' 제거", originalName);
    }

    public boolean isConfirmed(String originalName) {
        return redisTemplate.opsForHash().hasKey(CORRECTION_CONFIRMED_KEY, originalName);
    }

    public Optional<String> findConfirmedCorrection(String originalName) {
        Object cached = redisTemplate.opsForHash().get(CORRECTION_CONFIRMED_KEY, originalName);
        return Optional.ofNullable(cached).map(Object::toString);
    }
}