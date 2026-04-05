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
 * 브랜드명이 변경된 부정 피드백을 처리하는 개선 핸들러입니다.
 *
 * <h3>진입 경로</h3>
 * <p>
 * {@code REFNegativeFeedbackDispatcher}가 {@code REFCorrectionDiff}를 분석하여
 * {@code changedFields}에 {@link REFCorrectionType#BRAND}가 포함된 경우
 * 이 핸들러의 {@link #handle(REFNegativeFeedbackEvent)}를 호출합니다.
 * 제품명, 카테고리 등 다른 필드와 동시에 브랜드가 변경된 경우에도
 * 각 핸들러가 독립적으로 실행되므로 이 핸들러는 브랜드 처리에만 집중합니다.
 * </p>
 *
 * <h3>핵심 역할 — 브랜드 사전 자동 반영</h3>
 * <p>
 * 사용자가 직접 입력한 브랜드명을 Redis 카운터로 집계하고,
 * {@link #MIN_BRAND_COUNT}(2회) 이상 동일하게 입력되면
 * 브랜드 사전({@code recognition:dict:brand}) PENDING Set에 추가합니다.
 * PENDING이 {@link #BATCH_SIZE}(20개)에 도달하거나 매일 새벽 3시 스케줄러가 실행되면
 * ACTIVE Set으로 이동하여 {@code REFAhoCorasickBrandNameMatcher}가 Trie를 재빌드합니다.
 * 이후 동일 브랜드명이 포함된 제품명 인식 시 파서가 브랜드를 자동으로 추출합니다.
 * </p>
 *
 * <h3>전체 처리 흐름</h3>
 * <pre>
 * REFNegativeFeedbackDispatcher
 *   │
 *   └─▶ handle(event)
 *         │
 *         ├─ [Step 1] INCR feedback:brand:{correctedBrand}
 *         │
 *         ├─ [Step 2] count == 1?
 *         │     └─ YES: TTL 만료 후 첫 접근 가능성
 *         │             DB countByCorrectBrandName() 조회
 *         │             dbCount > 1이면 Redis SET으로 복원
 *         │
 *         ├─ [Step 3] TTL 30일 갱신 (활동 기준 만료 연장)
 *         │
 *         ├─ count < MIN_BRAND_COUNT(2)? → 즉시 리턴
 *         │
 *         ├─ [Step 4] REFBrandDictionaryFlushService.addToPending(correctedBrand)
 *         │             이미 ACTIVE에 있으면 PENDING 추가 생략
 *         │
 *         └─ [Step 5] pendingSize >= BATCH_SIZE(20)?
 *               └─ YES → brandDictionaryFlushService.flush()
 *                         PENDING → ACTIVE 이동
 *                         REFDictionarySyncedEvent 발행
 *                         → REFAhoCorasickBrandNameMatcher.rebuild()
 * </pre>
 *
 * <h3>검수 큐 미사용 이유</h3>
 * <p>
 * 브랜드명은 사용자가 {@code correctionSuggestions}에서 선택하는 것이 아니라
 * 직접 텍스트로 입력하는 필드입니다.
 * 동일한 브랜드명을 여러 사용자가 독립적으로 입력했다는 사실 자체가 검증이 됩니다.
 * 악용 사용자가 엉터리 브랜드를 입력해도 다른 사용자들이 동일한 값을 반복 입력할
 * 가능성이 낮아 {@link #MIN_BRAND_COUNT} 임계값이 자연스럽게 방어막 역할을 합니다.
 * </p>
 *
 * <h3>Redis 데이터 구조</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead>
 *     <tr style="background:#f0f0f0;">
 *       <th>키</th>
 *       <th>타입</th>
 *       <th>TTL</th>
 *       <th>설명</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code feedback:brand:{correctedBrand}}</td>
 *       <td>String</td>
 *       <td>30일 (활동 시 갱신)</td>
 *       <td>브랜드명 입력 횟수 카운터 (이 핸들러 관리)</td>
 *     </tr>
 *     <tr>
 *       <td>{@code recognition:dict:brand:pending}</td>
 *       <td>Set</td>
 *       <td>없음</td>
 *       <td>임계값 도달 브랜드 대기열 (FlushService 관리)</td>
 *     </tr>
 *     <tr>
 *       <td>{@code recognition:dict:brand}</td>
 *       <td>Set</td>
 *       <td>없음</td>
 *       <td>ACTIVE 브랜드 사전, Trie 빌드 대상 (FlushService 관리)</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>TTL 만료 후 Redis miss 복원 전략</h3>
 * <p>
 * {@code INCR} 결과가 1이면 키가 새로 생성된 것으로, 두 가지 케이스를 의미합니다.
 * </p>
 * <ul>
 *   <li><b>Case A (진짜 첫 피드백):</b> DB 카운트도 1 이하 → 복원 없이 진행</li>
 *   <li><b>Case B (TTL 만료 후 재진입):</b> DB에 누적 이력 존재 → Redis {@code SET}으로 복원</li>
 * </ul>
 * <p>
 * Redis가 살아있는 정상 케이스에서는 count가 항상 2 이상이므로 DB를 조회하지 않습니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see REFNegativeFeedbackDispatcher
 * @see REFBrandDictionaryFlushService
 * @see com.refridge.core_server.recognition_feedback.infra.brand.REFBrandDictionaryScheduler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFBrandImprovementHandler implements REFImprovementActionHandler{

    private final StringRedisTemplate redisTemplate;
    private final REFBrandDictionaryFlushService brandDictionaryFlushService;
    private final REFRecognitionFeedbackRepository feedbackRepository;

    /** 브랜드명 입력 횟수 카운터 키 접두사 ({@code feedback:brand:{correctedBrand}}) */
    private static final String BRAND_COUNTER_PREFIX = "feedback:brand:";

    /**
     * 브랜드 사전 PENDING 추가를 위한 최소 입력 횟수.
     * 동일 브랜드명이 이 횟수 이상 독립적으로 입력되어야 사전 추가 후보가 됩니다.
     */
    public static final int MIN_BRAND_COUNT = 2;

    /**
     * PENDING Set이 이 크기에 도달하면 즉시 flush를 트리거합니다.
     * 그 외에는 {@code REFBrandDictionaryScheduler}가 매일 새벽 3시에 처리합니다.
     */
    private static final int BATCH_SIZE = 20;

    /**
     * 30일간 해당 브랜드에 대한 피드백이 없으면 카운터 키 자동 만료.
     * 만료 후 첫 피드백 도달 시 DB에서 복원 (Step 2 참고).
     * PENDING/ACTIVE Set 키는 TTL을 설정하지 않습니다.
     * 한 번 사전에 등록된 브랜드는 영구 유지되어야 하기 때문입니다.
     */
    private static final Duration COUNTER_TTL = Duration.ofDays(30);

    /**
     * 이 핸들러가 처리하는 변경 유형.
     * {@code REFNegativeFeedbackDispatcher}가 EnumMap 등록 시 키로 사용합니다.
     */
    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.BRAND;
    }

    /**
     * 브랜드명 수정 피드백을 처리합니다.
     *
     * <p>
     * 수정된 브랜드명이 null이거나 공백이면 처리를 생략합니다.
     * 브랜드는 {@code correctionSuggestions}에 노출되지 않으므로
     * 사용자가 직접 타이핑한 값만 들어옵니다.
     * </p>
     *
     * @param event 부정 피드백 도메인 이벤트
     *              ({@code correctedBrandName}: 사용자가 직접 입력한 브랜드명)
     */
    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String correctedBrand = event.correction().getCorrectedBrandName();

        // 브랜드명 미입력 케이스 — 사용자가 브랜드 필드를 비워둔 경우
        if (correctedBrand == null || correctedBrand.isBlank()) return;

        String counterKey = BRAND_COUNTER_PREFIX + correctedBrand;

        // ── Step 1: 카운터 증가 ───────────────────────────────────
        // 키가 없으면 0에서 시작하여 +1 (Redis INCR 기본 동작)
        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count == null) {
            log.warn("[브랜드 핸들러] Redis increment 실패. brand='{}'", correctedBrand);
            return;
        }

        // ── Step 2: Redis miss 복원 ───────────────────────────────
        // count == 1이면 두 가지 케이스:
        //   Case A (진짜 첫 피드백): DB 카운트도 1 이하 → 복원 없이 진행
        //   Case B (TTL 만료 후 재진입): DB에 누적 이력 존재 → SET으로 복원
        // count >= 2이면 Redis 살아있는 정상 케이스 → DB 조회 불필요
        if (count == 1) {
            long dbCount = feedbackRepository.countByCorrectBrandName(correctedBrand);
            if (dbCount > 1) {
                // Case B: increment로 이미 1이 세팅된 상태를 DB 실제 횟수로 덮어씀
                redisTemplate.opsForValue().set(counterKey, String.valueOf(dbCount));
                count = dbCount;
                log.info("[브랜드 핸들러] Redis 복원. brand='{}', dbCount={}", correctedBrand, dbCount);
            }
            // Case A: dbCount <= 1 → 진짜 첫 피드백, 복원 없이 그대로 진행
        }

        // ── Step 3: TTL 갱신 ──────────────────────────────────────
        // 피드백이 올 때마다 30일 TTL 리셋
        // Step 2 이후에 실행되어야 복원 직후 TTL이 즉시 세팅됨
        redisTemplate.expire(counterKey, COUNTER_TTL);

        log.info("[브랜드 핸들러] brand='{}', count={}, feedbackId={}",
                correctedBrand, count, event.feedbackId().getValue());

        // 임계값 미달 — 사전 추가 불필요
        if (count < MIN_BRAND_COUNT) return;

        // ── Step 4: PENDING 추가 ──────────────────────────────────
        // 이미 ACTIVE 사전에 있는 브랜드는 FlushService 내부에서 스킵됨
        Long pendingSize = brandDictionaryFlushService.addToPending(correctedBrand);
        log.info("[브랜드 핸들러] PENDING 추가. brand='{}', pendingSize={}", correctedBrand, pendingSize);

        // ── Step 5: BATCH_SIZE 즉시 flush ─────────────────────────
        // BATCH_SIZE 미달이면 REFBrandDictionaryScheduler(새벽 3시)가 잔여 PENDING 처리
        // flush() 내부에서 분산 락(SET NX PX)으로 다중 인스턴스 중복 실행 방지
        if (pendingSize != null && pendingSize >= BATCH_SIZE) {
            log.info("[브랜드 핸들러] BATCH_SIZE({}) 도달, 즉시 flush.", BATCH_SIZE);
            brandDictionaryFlushService.flush();
        }
    }

}