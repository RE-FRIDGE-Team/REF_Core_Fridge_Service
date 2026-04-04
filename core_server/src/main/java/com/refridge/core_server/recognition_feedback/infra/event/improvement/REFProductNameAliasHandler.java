package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import com.refridge.core_server.recognition_feedback.infra.persistence.dto.REFFeedbackBrandCorrectionCountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * <h3>제품명 수정 피드백 핸들러</h3>
 * 제품명이 변경된 부정 피드백을 처리하는 개선 핸들러입니다.
 *
 * <h3>진입 경로</h3>
 * <p>
 * {@link REFNegativeFeedbackDispatcher}가 {@code REFCorrectionDiff}를 분석하여
 * {@code changedFields}에 {@link REFCorrectionType#PRODUCT_NAME}이 포함된 경우
 * 이 핸들러의 {@link #handle(REFNegativeFeedbackEvent)}를 호출합니다.
 * </p>
 *
 * <h3>핵심 역할 — alias 후보 집계 및 자동 확정</h3>
 * <p>
 * 사용자가 수정한 제품명을 Redis Hash에 누적하고,
 * 3중 게이트 조건을 충족하면 해당 수정본을 공식 alias로 자동 확정합니다.
 * 확정된 alias는 이후 인식 파이프라인 응답에서 원본 제품명 대신 노출됩니다.
 * </p>
 *
 * <h3>전체 처리 흐름</h3>
 * <pre>
 * REFNegativeFeedbackDispatcher
 *   │
 *   └─▶ handle(event)
 *         │
 *         ├─ incrementCandidateCounts()
 *         │     ├─ Redis HINCR: correctedName 선택 횟수 +1
 *         │     └─ Redis HINCR: __total__ +1
 *         │
 *         ├─ restoreFromDbIfMiss()
 *         │     └─ occurrenceCount == 1?
 *         │           ├─ Case A (첫 피드백): DB 비어있음 → 그대로 진행
 *         │           └─ Case B (TTL 만료):  DB 이력 존재 → Hash 전체 복원
 *         │
 *         ├─ TTL 갱신 (30일)
 *         │
 *         └─ evaluateAndConfirmAlias()
 *               ├─ Gate 1: occurrenceCount < MIN_ALIAS_COUNT(10) → 즉시 리턴
 *               ├─ Gate 2: occurrenceCount / __total__ >= 0.7
 *               ├─ Gate 3: occurrenceCount / 2위 후보 >= 3.0
 *               │
 *               ├─ 통과 → confirmAlias()
 *               └─ 미달 + CONFIRMED 상태 → reopenAlias()
 * </pre>
 *
 * <h3>Redis 데이터 구조</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead>
 *     <tr style="background:#000000;">
 *       <th>키</th>
 *       <th>타입</th>
 *       <th>TTL</th>
 *       <th>설명</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code feedback:product-alias:{originalName}}</td>
 *       <td>Hash</td>
 *       <td>30일 (활동 시 갱신)</td>
 *       <td>수정본별 선택 횟수 + {@code __total__} 필드</td>
 *     </tr>
 *     <tr>
 *       <td>{@code alias:confirmed}</td>
 *       <td>Hash</td>
 *       <td>없음</td>
 *       <td>원본명 → 확정 alias명 매핑 (REFAliasConfirmationService 관리)</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>__total__ 설계 의도</h3>
 * <p>
 * {@code __total__}은 부정 피드백(수정본 선택)뿐 아니라
 * 긍정 피드백(승인)도 포함한 전체 사용자 반응 수입니다.
 * Gate 2의 분모로 사용되어, 다수의 정상 사용자가 승인한 경우
 * 소수 악용 사용자의 수정본이 0.7 비율을 넘지 못하도록 방어합니다.
 * </p>
 *
 * <h3>TTL 만료 후 Redis miss 복원 전략</h3>
 * <p>
 * {@code HINCR} 결과 {@code occurrenceCount == 1}이면 이 필드가 새로 생성된 것입니다.
 * 인기 없는 제품의 Hash가 30일 TTL 만료 후 첫 피드백이 도달한 상황일 수 있습니다.
 * 이 경우 DB에서 누적 이력을 조회하여 Hash 전체를 복원하므로,
 * 이전 피드백 이력이 초기화되지 않고 정확한 카운트가 유지됩니다.
 * Redis가 살아있는 정상 케이스에서는 DB를 전혀 조회하지 않습니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 3.
 * @see REFNegativeFeedbackDispatcher
 * @see REFAliasConfirmationService
 * @see com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameAliasHandler implements REFImprovementActionHandler {

    private final StringRedisTemplate redisTemplate;
    private final REFAliasConfirmationService aliasConfirmationService;
    private final REFRecognitionFeedbackRepository feedbackRepository;

    private static final String ALIAS_CANDIDATE_PREFIX = "feedback:product-alias:";

    /**
     * 30일간 해당 제품명에 대한 피드백 활동이 없으면 Hash 자동 만료.
     * 만료 후 첫 피드백 도달 시 DB에서 복원 ({@link #restoreFromDbIfMiss} 참고).
     */
    private static final Duration CANDIDATE_TTL = Duration.ofDays(30);

    /**
     * 이 핸들러가 처리하는 변경 유형.
     * {@code REFNegativeFeedbackDispatcher}가 EnumMap 등록 시 키로 사용합니다.
     */
    @Override
    public REFCorrectionType supportedType() {
        return REFCorrectionType.PRODUCT_NAME;
    }

    /**
     * 제품명 수정 피드백의 전체 처리를 조율합니다.
     *
     * <p>
     * 원본 제품명과 수정 제품명이 동일하면 생략합니다.
     * 이는 사용자가 다른 필드만 수정하고 제품명은 그대로 둔 경우이며,
     * 묵시적 긍정으로 간주합니다.
     * </p>
     *
     * @param event 부정 피드백 도메인 이벤트
     */
    @Override
    public void handle(REFNegativeFeedbackEvent event) {
        String originalName = event.snapshot().getProductName();
        String correctedName = event.correction().getCorrectedProductName();

        // 제품명 미변경 — 다른 필드(브랜드, 카테고리 등)만 수정된 케이스
        if (originalName == null || correctedName == null) return;
        if (originalName.equals(correctedName)) return;

        String hashKey = ALIAS_CANDIDATE_PREFIX + originalName;

        // Redis 카운터 증가
        CandidateCounts counts = incrementCandidateCounts(hashKey, correctedName);
        if (counts == null) return;

        // TTL 만료 후 재진입이면 DB에서 Hash 전체 복원
        counts = restoreFromDbIfMiss(hashKey, originalName, correctedName, counts);

        // 피드백 활동 기준으로 TTL 30일 리셋
        // restoreFromDbIfMiss() 이후에 실행되어야 복원 직후 TTL이 즉시 세팅됨
        redisTemplate.expire(hashKey, CANDIDATE_TTL);

        log.info("[Alias 핸들러] '{}' → '{}', 이 후보={}회, 전체={}회, feedbackId={}",
                originalName, correctedName,
                counts.occurrenceCount(), counts.totalCount(),
                event.feedbackId().getValue());

        // 3중 게이트 검사 및 alias 확정/reopen
        evaluateAndConfirmAlias(hashKey, originalName, correctedName, counts);
    }

    /**
     * Redis Hash에 수정본 선택 횟수와 전체 반응 횟수를 각각 1씩 증가시킵니다.
     *
     * @param hashKey      {@code feedback:product-alias:{originalName}}
     * @param correctedName 사용자가 선택한 수정 제품명
     * @return 증가 후 현재 카운트. Redis 장애 시 {@code null} 반환
     */
    private CandidateCounts incrementCandidateCounts(String hashKey, String correctedName) {
        // Hash 필드가 없으면 0에서 시작하여 +1 (Redis HINCR 기본 동작)
        Long occurrenceCount = redisTemplate.opsForHash()
                .increment(hashKey, correctedName, 1);

        // __total__ = 부정(수정본 선택) + 긍정(승인) 합계
        // 긍정 피드백의 __total__ 증가는 REFPositiveFeedbackAggregationHandler가 담당
        Long totalCount = redisTemplate.opsForHash()
                .increment(hashKey, REFAliasConfirmationService.TOTAL_FIELD, 1);

        if (occurrenceCount == null || totalCount == null) {
            log.warn("[Alias 핸들러] Redis increment 실패. hashKey='{}'", hashKey);
            return null;
        }

        return new CandidateCounts(occurrenceCount, totalCount);
    }

    /**
     * TTL 만료 후 첫 접근인 경우 DB에서 alias 후보 이력을 조회하여 Hash를 복원합니다.
     *
     * <p>
     * {@code occurrenceCount == 1}은 두 가지 케이스를 의미합니다.
     * </p>
     * <ul>
     *   <li><b>Case A (진짜 첫 피드백):</b> DB가 비어있음 → 복원 없이 그대로 반환</li>
     *   <li><b>Case B (TTL 만료 후 재진입):</b> DB에 이전 이력 존재 → Hash 전체 복원 후 반환</li>
     * </ul>
     * <p>
     * {@code occurrenceCount >= 2}이면 Redis가 살아있는 정상 케이스이므로 DB를 조회하지 않습니다.
     * </p>
     *
     * @return DB 복원이 발생한 경우 복원된 카운트, 그렇지 않으면 원본 카운트
     */
    private CandidateCounts restoreFromDbIfMiss(String hashKey,
                                                String originalName,
                                                String correctedName,
                                                CandidateCounts counts) {
        // occurrenceCount >= 2이면 Redis 살아있는 정상 케이스 — DB 조회 불필요
        if (counts.occurrenceCount() != 1) return counts;

        List<REFFeedbackBrandCorrectionCountDto> dbCounts =
                feedbackRepository.findAliasCandidateCountsByOriginalName(originalName);

        // Case A: DB도 비어있음 = 진짜 첫 피드백 → 복원 없이 진행
        if (dbCounts.isEmpty()) return counts;

        // Case B: TTL 만료 후 재진입 → DB 데이터로 Hash 전체 복원
        long dbTotal = 0L;
        for (REFFeedbackBrandCorrectionCountDto dto : dbCounts) {
            // 각 수정본 후보의 누적 횟수를 Hash에 덮어씀
            // (increment로 이미 1이 들어간 상태이지만 DB 값이 더 정확함)
            redisTemplate.opsForHash().put(
                    hashKey,
                    dto.correctedProductName(),
                    String.valueOf(dto.selectionCount())
            );
            dbTotal += dto.selectionCount();
        }

        // __total__을 CORRECTED 피드백 합계로 복원
        // 긍정 피드백 분은 REFPositiveFeedbackAggregationHandler가 이후 자연스럽게 재누적
        redisTemplate.opsForHash().put(
                hashKey,
                REFAliasConfirmationService.TOTAL_FIELD,
                String.valueOf(dbTotal)
        );

        // 현재 처리 중인 correctedName이 DB에 없으면(첫 선택) occurrenceCount는 1로 유지
        long restoredOccurrence = dbCounts.stream()
                .filter(d -> correctedName.equals(d.correctedProductName()))
                .mapToLong(REFFeedbackBrandCorrectionCountDto::selectionCount)
                .findFirst()
                .orElse(1L);

        log.info("[Alias 핸들러] Redis 복원. originalName='{}', 후보수={}, total={}",
                originalName, dbCounts.size(), dbTotal);

        return new CandidateCounts(restoredOccurrence, dbTotal);
    }

    /**
     * 3중 게이트를 검사하여 alias 확정 또는 reopen을 처리합니다.
     *
     * <p>Gate 1은 HGETALL 비용을 피하기 위한 빠른 차단입니다.
     * Gate 2, 3은 {@link REFAliasConfirmationService#meetsConfirmationThreshold}에서 평가됩니다.</p>
     *
     * <ul>
     *   <li><b>Gate 1:</b> 이 후보 선택 횟수 >= MIN_ALIAS_COUNT(10)</li>
     *   <li><b>Gate 2:</b> 이 후보 / __total__ >= 0.7</li>
     *   <li><b>Gate 3:</b> 이 후보 / 2위 후보 >= 3.0</li>
     * </ul>
     *
     * @param hashKey        Redis Hash 키
     * @param originalName   원본 정제 제품명
     * @param correctedName  사용자가 선택한 수정 제품명
     * @param counts         현재 카운트 (복원 반영된 최신 값)
     */
    private void evaluateAndConfirmAlias(String hashKey,
                                         String originalName,
                                         String correctedName,
                                         CandidateCounts counts) {
        // Gate 1 빠른 차단 — HGETALL 이전에 최소 횟수 미달이면 즉시 종료
        if (counts.occurrenceCount() < REFAliasConfirmationService.MIN_ALIAS_COUNT) return;

        // Gate 1 통과 시에만 HGETALL 실행 (전체 후보 맵 한 번에 조회)
        Map<String, Long> allCounts = aliasConfirmationService.getAllCandidateCounts(hashKey);

        boolean meetsThreshold = aliasConfirmationService.meetsConfirmationThreshold(
                correctedName, counts.occurrenceCount(), counts.totalCount(), allCounts);

        if (meetsThreshold) {
            // 3중 게이트 통과 → alias 확정
            // DB: REFProductNameAlias 상태 CONFIRMED
            // Redis: alias:confirmed Hash에 originalName → correctedName 등록
            // 이후 REFProductRecognitionAppService.applyAliasToResult()에서 자동 적용
            aliasConfirmationService.confirmAlias(
                    originalName, correctedName,
                    counts.occurrenceCount(), counts.totalCount());
            log.info("[Alias 핸들러] alias 확정. '{}' → '{}'", originalName, correctedName);

        } else if (aliasConfirmationService.isConfirmed(originalName)) {
            // Gate 미달 + 기존 CONFIRMED alias 존재 → 경쟁 후보 재부상
            // DB: REFProductNameAlias 상태 CANDIDATE
            // Redis: alias:confirmed에서 originalName 키 삭제
            // → 파이프라인 응답에서 alias 교체 즉시 중단, 재경쟁 시작
            log.info("[Alias 핸들러] 경쟁 후보 재부상, reopen. originalName='{}'", originalName);
            aliasConfirmationService.reopenAlias(originalName);
        }
    }

    /**
     * Redis HINCR 결과로 얻은 수정본 선택 횟수와 전체 반응 횟수를 함께 전달하기 위한
     * 불변 값 객체입니다.
     *
     * <p>
     * {@link #restoreFromDbIfMiss}에서 DB 복원이 발생하면 새 인스턴스로 교체되어
     * 이후 Gate 검사가 복원된 정확한 값 기준으로 동작합니다.
     * </p>
     *
     * @param occurrenceCount 현재 처리 중인 수정본의 선택 횟수
     * @param totalCount      전체 사용자 반응 횟수 (긍정 + 모든 수정본 합)
     */
    private record CandidateCounts(long occurrenceCount, long totalCount) {}
}