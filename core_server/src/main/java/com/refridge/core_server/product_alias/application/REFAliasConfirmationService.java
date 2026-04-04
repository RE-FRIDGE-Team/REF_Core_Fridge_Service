package com.refridge.core_server.product_alias.application;

import com.refridge.core_server.product_alias.domain.REFProductNameAlias;
import com.refridge.core_server.product_alias.domain.REFProductNameAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 제품명 alias의 확정 및 재심사를 처리하는 Application Service입니다.
 *
 * <h3>alias란</h3>
 * <p>
 * 인식 파이프라인이 반환한 원본 정제 제품명({@code originalName})을
 * 다수의 사용자가 동의한 수정 제품명({@code aliasName})으로 대체하는 매핑입니다.
 * alias가 확정되면, 이후 동일한 원본 제품명이 파이프라인에서 인식될 때
 * {@code REFProductRecognitionAppService.applyAliasToResult()}가
 * 응답 수준에서 자동으로 alias명을 노출합니다.
 * </p>
 *
 * <h3>이 서비스의 위치</h3>
 * <p>
 * {@code product_alias} 도메인의 Application Layer에 위치합니다.
 * {@code recognition_feedback} 도메인의 {@code REFProductNameAliasHandler}가
 * 피드백 집계 결과를 기반으로 이 서비스를 호출합니다.
 * </p>
 *
 * <h3>alias 라이프사이클</h3>
 * <pre>
 *   [부정 피드백 누적]
 *         │
 *         ▼
 *   CANDIDATE ──── 3중 게이트 통과 ────▶ CONFIRMED
 *         ▲                                  │
 *         │                                  │ 경쟁 후보 재부상
 *         └──────── reopenAlias() ───────────┘
 *
 *   CONFIRMED 상태:
 *     - DB: REFProductNameAlias.status = CONFIRMED
 *     - Redis: alias:confirmed Hash에 originalName → aliasName 등록
 *     - 파이프라인 응답: alias명 자동 교체 활성화
 *
 *   CANDIDATE 상태 (reopen 후):
 *     - DB: REFProductNameAlias.status = CANDIDATE
 *     - Redis: alias:confirmed에서 키 삭제
 *     - 파이프라인 응답: alias 교체 즉시 중단, correctionSuggestions 재노출
 * </pre>
 *
 * <h3>alias 확정 조건 — 3중 게이트</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead>
 *     <tr style="background:#f0f0f0;">
 *       <th>게이트</th>
 *       <th>조건</th>
 *       <th>목적</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td><b>Gate 1</b></td>
 *       <td>이 후보 선택 횟수 >= {@link #MIN_ALIAS_COUNT}(10)</td>
 *       <td>절대 샘플 수 보장. 소수 피드백으로 alias 확정 방지</td>
 *     </tr>
 *     <tr>
 *       <td><b>Gate 2</b></td>
 *       <td>이 후보 / {@code __total__} >= {@link #MIN_ALIAS_RATIO}(0.7)</td>
 *       <td>전체 반응 대비 지지율. 긍정 피드백이 분모에 포함되어 악용 방지</td>
 *     </tr>
 *     <tr>
 *       <td><b>Gate 3</b></td>
 *       <td>이 후보 / 2위 후보 >= {@link #DOMINANCE_RATIO}(3.0)</td>
 *       <td>경쟁 후보가 재부상할 때 성급한 확정 방지</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>Gate 2 분모에 긍정 피드백이 포함되는 이유</h3>
 * <p>
 * {@code __total__}은 부정 피드백(수정본 선택)뿐 아니라 긍정 피드백(승인)도 포함합니다.
 * 만약 분모가 수정본 합계만이라면, 998명이 승인하고 2명이 엉터리 수정본을 선택해도
 * Gate 2 비율이 2/2 = 100%가 되어 alias로 확정됩니다.
 * 긍정 피드백을 분모에 포함하면 2/1000 = 0.2%로 0.7 미달이 되어 확정 불가합니다.
 * {@code __total__} 증가는 {@code REFPositiveFeedbackAggregationHandler}가 담당합니다.
 * </p>
 *
 * <h3>Redis 2-레이어 구조</h3>
 * <pre>
 *   ┌ 후보 집계 레이어 (AliasHandler, PositiveHandler 관리) ─────────────┐
 *   │ Hash: feedback:product-alias:{originalName}                   │
 *   │   Field: {correctedName1} → 수정본 선택 횟수                      │
 *   │   Field: {correctedName2} → 수정본 선택 횟수                      │
 *   │   Field: __total__        → 전체 반응 횟수 (긍정 포함)             │
 *   │   TTL: 30일 (활동 시 갱신)                                       │
 *   └───────────────────────────────────────────────────────────────┘
 *
 *   ┌ 확정 캐시 레이어 (이 서비스 관리) ────────────────────────────────┐
 *   │ Hash: alias:confirmed                                      │
 *   │   Field: {originalName} → {aliasName}                      │
 *   │   TTL: 없음 (명시적 삭제만)                                     │
 *   └────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @author 이승훈
 * @see com.refridge.core_server.recognition_feedback.infra.event.improvement.REFProductNameAliasHandler
 * @see com.refridge.core_server.product_recognition.application.REFProductRecognitionAppService
 * @since 2026. 4. 3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFAliasConfirmationService {

    private final REFProductNameAliasRepository aliasRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * alias 확정을 위한 최소 선택 횟수 (Gate 1)
     */
    public static final int MIN_ALIAS_COUNT = 10;

    /**
     * alias 확정을 위한 최소 지지율 — 이 후보 / __total__ (Gate 2)
     */
    public static final double MIN_ALIAS_RATIO = 0.7;

    /**
     * alias 확정을 위한 최소 우위 배율 — 1위 / 2위 (Gate 3)
     */
    public static final double DOMINANCE_RATIO = 3.0;

    /**
     * 확정된 alias 캐시 Hash 키. originalName → aliasName 매핑 저장
     */
    public static final String ALIAS_CONFIRMED_KEY = "alias:confirmed";

    /**
     * alias 후보 Hash에서 전체 반응 횟수를 저장하는 특수 필드명
     */
    public static final String TOTAL_FIELD = "__total__";

    /**
     * 3중 게이트를 모두 통과하는지 검사합니다.
     *
     * <p>
     * Gate 1은 {@code REFProductNameAliasHandler}에서 HGETALL 이전에 이미 검사합니다.
     * 이 메서드는 Gate 1을 재검사한 뒤 Gate 2, 3을 추가로 평가합니다.
     * </p>
     *
     * <h3>Gate 3 상세</h3>
     * <p>
     * {@code allCounts}에서 현재 후보({@code candidateKey})와 {@code __total__} 필드를 제외한
     * 나머지 후보들 중 최댓값을 2위 후보로 간주합니다.
     * 2위 후보가 없거나 0이면 Gate 3는 자동 통과합니다.
     * </p>
     *
     * @param candidateKey    게이트를 검사할 수정 제품명 (현재 처리 중인 후보)
     * @param occurrenceCount 이 후보의 선택 횟수
     * @param totalCount      {@code __total__} 값 (긍정 + 모든 수정본 합계)
     * @param allCounts       {@code HGETALL} 결과 — 모든 후보 및 {@code __total__} 포함
     * @return 3중 게이트를 모두 통과하면 {@code true}
     */
    public boolean meetsConfirmationThreshold(String candidateKey, long occurrenceCount, long totalCount, Map<String, Long> allCounts) {

        // Gate 1: 절대 샘플 수 보장
        // 소수 피드백으로 alias가 성급하게 확정되는 것을 방지
        if (occurrenceCount < MIN_ALIAS_COUNT) return false;

        // Gate 2: 전체 반응 대비 지지율
        // 분모(__total__)에 긍정 피드백이 포함되어 있어
        // 다수의 정상 사용자 승인이 악용 사용자의 수정본 확정을 자동 방어
        if (totalCount == 0) return false;
        double ratio = (double) occurrenceCount / totalCount;
        if (ratio < MIN_ALIAS_RATIO) return false;

        // Gate 3: 경쟁 후보 대비 지배적 우위
        // 현재 후보와 __total__ 필드를 제외한 나머지 후보 중 최댓값 = 2위
        OptionalLong secondMax = allCounts.entrySet().stream().filter(e -> !e.getKey().equals(candidateKey)).filter(e -> !e.getKey().equals(TOTAL_FIELD)).mapToLong(Map.Entry::getValue).max();

        if (secondMax.isPresent() && secondMax.getAsLong() > 0) {
            double dominance = (double) occurrenceCount / secondMax.getAsLong();
            if (dominance < DOMINANCE_RATIO) {
                // 경쟁 후보와의 격차가 부족 — 아직 확정하기 이름
                log.debug("[Alias 확정 보류] 경쟁 후보 존재. 1위={}, 2위={}, 배율={:.2f}", occurrenceCount, secondMax.getAsLong(), dominance);
                return false;
            }
        }
        // 2위 후보 없음 or 2위가 0 → Gate 3 자동 통과

        return true;
    }

    /**
     * Redis Hash에서 alias 후보별 선택 횟수 전체를 조회합니다.
     *
     * <p>
     * {@code HGETALL} 명령으로 Hash 전체를 한 번에 읽습니다.
     * {@code __total__} 필드도 포함되어 반환되며,
     * {@link #meetsConfirmationThreshold}에서 Gate 3 계산 시
     * {@code __total__} 필드를 명시적으로 제외합니다.
     * </p>
     *
     * <p>
     * Redis 값은 {@code String}으로 저장되므로 {@code Long}으로 파싱합니다.
     * 파싱 불가 값은 조용히 무시합니다.
     * </p>
     *
     * @param hashKey {@code feedback:product-alias:{originalName}}
     * @return 후보명 → 선택 횟수 맵 ({@code __total__} 필드 포함)
     */
    public Map<String, Long> getAllCandidateCounts(String hashKey) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(hashKey);
        Map<String, Long> result = new HashMap<>();
        raw.forEach((k, v) -> {
            try {
                result.put(k.toString(), Long.parseLong(v.toString()));
            } catch (NumberFormatException ignored) {
                // 정상적인 상황에서는 발생하지 않아야 함
            }
        });
        return result;
    }

    /**
     * alias를 확정하고 DB와 Redis에 동시에 반영합니다.
     *
     * <h3>처리 순서</h3>
     * <ol>
     *   <li>DB 조회: 기존 {@code REFProductNameAlias} 존재 여부 확인</li>
     *   <li>
     *     존재하면 횟수 갱신 + 상태가 CANDIDATE이면 CONFIRMED으로 전환<br>
     *     없으면 CANDIDATE로 새로 생성 후 즉시 CONFIRMED으로 전환
     *   </li>
     *   <li>DB 저장</li>
     *   <li>Redis {@code alias:confirmed} Hash에 {@code originalName → aliasName} 등록</li>
     * </ol>
     *
     * <h3>Redis alias:confirmed의 역할</h3>
     * <p>
     * {@code REFProductRecognitionAppService.applyAliasToResult()}가
     * 매 인식 요청마다 이 Hash를 {@code HGET}으로 O(1) 조회합니다.
     * DB 조회 없이 Redis에서 바로 alias 교체 여부를 결정하여 응답 성능을 보호합니다.
     * </p>
     *
     * @param originalName    파이프라인 원본 정제 제품명
     * @param aliasName       확정할 수정 제품명
     * @param occurrenceCount 이 alias의 선택 횟수 (DB 저장용)
     * @param totalCount      전체 반응 횟수 (DB 저장용)
     */
    @Transactional
    public void confirmAlias(String originalName, String aliasName, long occurrenceCount, long totalCount) {
        REFProductNameAlias alias = aliasRepository.findByOriginalName(originalName).map(existing -> {
            // 기존 레코드가 있으면 횟수 갱신 후 필요 시 상태 전환
            existing.updateCounts(occurrenceCount, totalCount);
            if (!existing.isConfirmed()) existing.confirm();
            return existing;
        }).orElseGet(() -> {
            // 첫 확정 — CANDIDATE로 생성 후 즉시 CONFIRMED 전환
            REFProductNameAlias newAlias = REFProductNameAlias.createCandidate(originalName, aliasName, occurrenceCount, totalCount);
            newAlias.confirm();
            return newAlias;
        });

        aliasRepository.save(alias);

        // Redis에 즉시 반영 — 다음 인식 요청부터 alias 교체 활성화
        redisTemplate.opsForHash().put(ALIAS_CONFIRMED_KEY, originalName, aliasName);

        log.info("[Alias 확정] '{}' → '{}', 횟수={}/{}, 비율={:.1f}%", originalName, aliasName, occurrenceCount, totalCount, (double) occurrenceCount / totalCount * 100);
    }

    /**
     * 확정된 alias를 재심사(CANDIDATE) 상태로 되돌립니다.
     *
     * <p>
     * 경쟁 후보가 Gate 3를 위협할 수준으로 재부상했을 때 호출됩니다.
     * DB 전환과 Redis 삭제가 함께 처리되며, Redis 삭제가 먼저 반영되어
     * 파이프라인 응답에서 alias 교체가 즉시 중단됩니다.
     * </p>
     *
     * <h3>reopen 이후 각 레이어 동작 변화</h3>
     * <ul>
     *   <li><b>파이프라인:</b> alias 교체 없이 원본명으로 ProductIndex 탐색 (정상)</li>
     *   <li><b>AppService:</b> {@code alias:confirmed HGET} → miss → alias 미적용</li>
     *   <li><b>correctionSuggestions:</b> 다시 노출되어 사용자 재투표 유도</li>
     * </ul>
     *
     * @param originalName reopen할 원본 제품명
     */
    @Transactional
    public void reopenAlias(String originalName) {
        aliasRepository.findByOriginalName(originalName).filter(REFProductNameAlias::isConfirmed).ifPresent(alias -> {
            alias.reopen();
            aliasRepository.save(alias);
            log.info("[Alias 재심사] '{}' CONFIRMED → CANDIDATE 전환", originalName);
        });

        // alias:confirmed에서 즉시 제거 — 다음 인식 요청부터 alias 교체 중단.
        // DB 전환보다 먼저 적용되더라도 무결성 문제 없음.
        // (alias:confirmed 없으면 파이프라인이 원본명 그대로 탐색하므로 안전)
        redisTemplate.opsForHash().delete(ALIAS_CONFIRMED_KEY, originalName);
        log.info("[Alias 재심사] Redis alias:confirmed 에서 '{}' 제거", originalName);
    }

    /**
     * 특정 원본 제품명이 현재 CONFIRMED 상태인지 확인합니다.
     *
     * <p>
     * DB 조회 없이 Redis {@code alias:confirmed} Hash의 키 존재 여부로 판단합니다.
     * {@code REFProductNameAliasHandler}에서 Gate 미달 시 reopen 여부 결정에 사용합니다.
     * </p>
     *
     * @param originalName 확인할 원본 제품명
     * @return {@code alias:confirmed} Hash에 해당 키가 존재하면 {@code true}
     */
    public boolean isConfirmed(String originalName) {
        return redisTemplate.opsForHash().hasKey(ALIAS_CONFIRMED_KEY, originalName);
    }

    /**
     * 확정된 alias를 Redis에서 O(1)으로 조회합니다.
     *
     * <p>
     * {@code REFProductRecognitionAppService.applyAliasToResult()}가
     * 매 인식 응답마다 호출하는 핵심 조회 메서드입니다.
     * {@code alias:confirmed} Hash에 키가 없으면 {@code Optional.empty()}를 반환하고,
     * 호출부에서 alias 미적용으로 처리합니다.
     * </p>
     *
     * @param originalName 파이프라인 원본 정제 제품명
     * @return 확정된 alias명. CONFIRMED 상태가 아니면 {@code empty}
     */
    public Optional<String> findConfirmedAlias(String originalName) {
        Object cached = redisTemplate.opsForHash().get(ALIAS_CONFIRMED_KEY, originalName);
        return Optional.ofNullable(cached).map(Object::toString);
    }
}