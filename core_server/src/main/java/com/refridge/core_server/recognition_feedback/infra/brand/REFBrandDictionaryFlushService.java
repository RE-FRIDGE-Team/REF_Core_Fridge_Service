package com.refridge.core_server.recognition_feedback.infra.brand;

import com.refridge.core_server.product_recognition.domain.event.REFDictionarySyncedEvent;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * 브랜드 사전 PENDING → ACTIVE 이동 및 Trie 재빌드를 담당하는 서비스입니다.
 *
 * <h3>Redis 키 구조</h3>
 * <pre>
 *   Set:    recognition:dict:brand          → ACTIVE (Trie 빌드 대상)
 *   Set:    recognition:dict:brand:pending  → PENDING (대기 중)
 *   String: brand:flush:lock                → 분산 락 (다중 인스턴스 중복 실행 방지)
 * </pre>
 *
 * <h3>동시성 보장</h3>
 * <ul>
 *   <li>분산 락 (SET NX PX): 다중 인스턴스 환경에서 flush() 중복 실행 방지</li>
 *   <li>SREM (DEL 대신): flush() 도중 addToPending()으로 추가된 브랜드 유실 방지</li>
 * </ul>
 *
 * <h3>케이스별 동작</h3>
 * <pre>
 *   케이스 1 (중복 flush): 락 획득 실패 → 즉시 리턴, 나중 flush에서 처리
 *   케이스 2 (rebuild 중 findMatch): volatile + read-once-local → 안전
 *   케이스 3 (flush 중 addToPending): SREM으로 조회한 멤버만 제거 → 신규 추가 보존
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFBrandDictionaryFlushService {

    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public static final String BRAND_ACTIVE_KEY  = "recognition:dict:brand";
    public static final String BRAND_PENDING_KEY = "recognition:dict:brand:pending";

    /**
     * 분산 락 키.
     * 다중 인스턴스 환경에서 스케줄러와 즉시 트리거가 동시에 flush()를 실행하는 것을 방지합니다.
     */
    private static final String FLUSH_LOCK_KEY = "brand:flush:lock";

    /**
     * 락 TTL: flush() 최대 예상 실행 시간보다 넉넉하게 설정.
     * Redis SADD + SREM + 이벤트 발행까지 통상 수 ms이나,
     * 네트워크 지연 등을 고려해 30초로 설정합니다.
     * 인스턴스가 락 보유 중 크래시되어도 30초 후 자동 해제됩니다.
     */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /**
     * 브랜드를 PENDING Set에 추가합니다.
     * 이미 ACTIVE에 있는 브랜드는 추가하지 않습니다 (중복 방지).
     *
     * @return 추가 후 PENDING 크기 (null이면 Redis 오류)
     */
    public Long addToPending(String brandName) {
        Boolean alreadyActive = redisTemplate.opsForSet()
                .isMember(BRAND_ACTIVE_KEY, brandName);
        if (Boolean.TRUE.equals(alreadyActive)) {
            log.debug("[브랜드 flush] 이미 ACTIVE 존재. brand='{}'", brandName);
            return redisTemplate.opsForSet().size(BRAND_PENDING_KEY);
        }

        redisTemplate.opsForSet().add(BRAND_PENDING_KEY, brandName);
        return redisTemplate.opsForSet().size(BRAND_PENDING_KEY);
    }

    /**
     * PENDING → ACTIVE 이동 후 Trie 재빌드를 트리거합니다.
     *
     * <h3>분산 락 (SET NX PX)</h3>
     * Redis의 SET NX PX 명령으로 원자적 락 획득을 시도합니다.
     * 다른 인스턴스(또는 동일 인스턴스의 다른 스레드)가 이미 flush() 중이면
     * 즉시 리턴합니다. 락을 획득한 인스턴스만 flush()를 실행합니다.
     *
     * <h3>SREM (DEL 대신)</h3>
     * members() 조회 후 DEL을 사용하면, 조회와 DEL 사이에
     * addToPending()으로 추가된 브랜드가 유실됩니다.
     * 조회한 멤버만 SREM으로 제거하면 그 사이 추가된 브랜드가 보존됩니다.
     */
    public void flush() {
        // ── Step 1: 분산 락 획득 시도 ────────────────────────────
        // SET brand:flush:lock "1" NX PX 30000
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(FLUSH_LOCK_KEY, "1", LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[브랜드 flush] 다른 인스턴스가 flush 중. 스킵.");
            return;
        }

        try {
            // ── Step 2: PENDING 조회 ──────────────────────────────
            Set<String> pending = redisTemplate.opsForSet().members(BRAND_PENDING_KEY);

            if (pending == null || pending.isEmpty()) {
                log.debug("[브랜드 flush] PENDING 비어있음, 스킵.");
                return;
            }

            int pendingCount = pending.size();
            log.info("[브랜드 flush] flush 시작. pendingCount={}", pendingCount);

            // ── Step 3: ACTIVE에 일괄 추가 ───────────────────────
            redisTemplate.opsForSet().add(
                    BRAND_ACTIVE_KEY,
                    pending.toArray(String[]::new)
            );

            // ── Step 4: 조회한 멤버만 SREM ────────────────────────
            // DEL 대신 SREM을 사용하는 이유:
            // members() 조회 후 DEL 사이에 addToPending()이 끼어들면
            // 새로 추가된 브랜드가 DEL로 함께 삭제되어 유실됨.
            // SREM은 조회 시점의 멤버만 제거하므로 그 이후 추가된 브랜드는 보존됨.
            redisTemplate.opsForSet().remove(
                    BRAND_PENDING_KEY,
                    pending.toArray(Object[]::new)
            );

            log.info("[브랜드 flush] {}건 PENDING → ACTIVE 이동 완료.", pendingCount);

            // ── Step 5: REFDictionarySyncedEvent 발행 ─────────────
            // → REFDictionarySyncEventHandler
            // → REFTrieMatcherRegistry.getMatcher(BRAND).rebuild()
            // → REFAhoCorasickBrandNameMatcher가 ACTIVE Set으로 Trie 재빌드
            eventPublisher.publishEvent(
                    new REFDictionarySyncedEvent(REFRecognitionDictionaryType.BRAND));

            log.info("[브랜드 flush] REFDictionarySyncedEvent 발행. Trie 재빌드 트리거.");

        } finally {
            // ── Step 6: 락 해제 ───────────────────────────────────
            // flush() 성공/실패 무관하게 반드시 해제
            redisTemplate.delete(FLUSH_LOCK_KEY);
            log.debug("[브랜드 flush] 분산 락 해제.");
        }
    }

    /**
     * PENDING Set의 현재 크기를 반환합니다.
     */
    public long getPendingSize() {
        Long size = redisTemplate.opsForSet().size(BRAND_PENDING_KEY);
        return size != null ? size : 0L;
    }
}