package com.refridge.core_server.product_alias.application;

import com.refridge.core_server.product_alias.domain.REFProductNameAlias;
import com.refridge.core_server.product_alias.domain.REFProductNameAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * alias 확정 도메인 서비스입니다.
 *
 * alias 확정 조건:
 *   수정본 선택 횟수 >= MIN_ALIAS_COUNT (10)
 *   AND 수정본 선택 비율 >= MIN_ALIAS_RATIO (0.7)
 *
 * 확정 시 처리:
 *   1. DB에 CONFIRMED 상태로 저장 (신규 or 기존 CANDIDATE 업데이트)
 *   2. Redis alias:confirmed Hash에 캐싱
 *
 * Redis 구조:
 *   Hash Key : alias:confirmed
 *   Field    : {originalName}
 *   Value    : {aliasName}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFAliasConfirmationService {

    private final REFProductNameAliasRepository aliasRepository;
    private final StringRedisTemplate redisTemplate;

    public static final int    MIN_ALIAS_COUNT = 10;
    public static final double MIN_ALIAS_RATIO = 0.7;

    /** Redis Hash 키 - CONFIRMED alias 전체 캐싱 */
    public static final String ALIAS_CONFIRMED_KEY = "alias:confirmed";

    /**
     * 주어진 횟수/비율이 확정 조건을 충족하는지 검사합니다.
     *
     * @param occurrenceCount     수정본 선택 누적 횟수
     * @param totalSelectionCount 전체 선택 횟수
     */
    public boolean meetsConfirmationThreshold(long occurrenceCount, long totalSelectionCount) {
        if (totalSelectionCount == 0) return false;
        double ratio = (double) occurrenceCount / totalSelectionCount;
        return occurrenceCount >= MIN_ALIAS_COUNT && ratio >= MIN_ALIAS_RATIO;
    }

    /**
     * alias를 확정합니다.
     * 이미 CANDIDATE row가 있으면 CONFIRMED로 전환하고,
     * 없으면 바로 CONFIRMED로 신규 생성합니다.
     * 확정 후 Redis Hash에 캐싱합니다.
     *
     * @param originalName        원본 정제 제품명
     * @param aliasName           확정할 alias 제품명
     * @param occurrenceCount     수정본 선택 횟수
     * @param totalSelectionCount 전체 선택 횟수
     */
    @Transactional
    public void confirmAlias(String originalName, String aliasName,
                             long occurrenceCount, long totalSelectionCount) {
        REFProductNameAlias alias = aliasRepository.findByOriginalName(originalName)
                .map(existing -> {
                    existing.updateCounts(occurrenceCount, totalSelectionCount);
                    if (!existing.isConfirmed()) {
                        existing.confirm();
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    REFProductNameAlias newAlias = REFProductNameAlias.createCandidate(
                            originalName, aliasName, occurrenceCount, totalSelectionCount);
                    newAlias.confirm();
                    return newAlias;
                });

        aliasRepository.save(alias);

        // Redis Hash에 캐싱 - 파싱 핸들러가 O(1)으로 조회
        redisTemplate.opsForHash().put(ALIAS_CONFIRMED_KEY, originalName, aliasName);

        log.info("[Alias 확정] '{}' -> '{}', 횟수={}, 비율={:.1f}%",
                originalName, aliasName, occurrenceCount,
                (double) occurrenceCount / totalSelectionCount * 100);
    }

    /**
     * 특정 원본 제품명에 대한 CONFIRMED alias를 Redis에서 조회합니다.
     * Redis miss 시 DB 폴백 없음 - 부팅 시 초기화로 보장.
     */
    public java.util.Optional<String> findConfirmedAlias(String originalName) {
        Object cached = redisTemplate.opsForHash().get(ALIAS_CONFIRMED_KEY, originalName);
        return java.util.Optional.ofNullable(cached).map(Object::toString);
    }
}