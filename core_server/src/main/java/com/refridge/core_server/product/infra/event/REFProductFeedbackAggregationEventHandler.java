package com.refridge.core_server.product.infra.event;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.ar.REFGroceryItem;
import com.refridge.core_server.product.application.REFProductLifeCycleService;
import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
import com.refridge.core_server.recognition_feedback.domain.event.REFProductFeedbackAggregationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * {@code REFProductFeedbackAggregationEvent}를 구독하여
 * 인식 파이프라인 결과를 Product로 자동 등록하는 핸들러입니다.
 *
 * <h3>이벤트 발행 경로</h3>
 * <pre>
 *   사용자 긍정 피드백
 *     → REFPositiveFeedbackAggregationHandler
 *         Redis 카운터(feedback:positive:{name}) 증가
 *         임계값 도달 시 DB 집계 → REFProductRegistrationPolicy 검증
 *         → REFProductFeedbackAggregationEvent 발행
 *             → 이 핸들러 (handle)
 * </pre>
 *
 * <h3>핵심 역할</h3>
 * <p>
 * 긍정 피드백이 등록 정책({@code REFProductRegistrationPolicy})을 충족하면
 * 파이프라인 인식 결과를 Product BC에 영속화합니다.
 * 이미 동일한 제품이 존재하는 경우({@code upsertProduct})에는 무시하므로
 * 중복 등록이 발생하지 않습니다.
 * </p>
 *
 * <h3>등록 제품명 결정 — alias 우선 적용</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead>
 *     <tr style="background:#f0f0f0;">
 *       <th>상황</th>
 *       <th>등록 제품명</th>
 *       <th>예시</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>alias CONFIRMED 상태</td>
 *       <td>alias명 (사용자 검증 완료)</td>
 *       <td>"해찬들 초고추장 340ml" → <b>"해찬들 초고추장"</b></td>
 *     </tr>
 *     <tr>
 *       <td>alias 없음 (미확정)</td>
 *       <td>파이프라인 원본 정제명</td>
 *       <td>"해찬들 초고추장 340ml" → <b>"해찬들 초고추장 340ml"</b></td>
 *     </tr>
 *   </tbody>
 * </table>
 * <p>
 * alias명으로 등록하는 이유: alias는 다수 사용자가 검증한 올바른 제품명입니다.
 * DB에 alias명으로 저장되어야 이후 {@code REFProductIndexSearchHandler}가
 * 동일 제품을 정확하게 매칭할 수 있습니다.
 * </p>
 *
 * <h3>원본명 기준 유지 원칙</h3>
 * <p>
 * alias는 오직 Product <b>등록 시의 제품명</b>에만 적용됩니다.
 * 아래 항목들은 alias와 무관하게 항상 원본 정제명 기준을 유지합니다.
 * </p>
 * <ul>
 *   <li>피드백 AR의 {@code orig_product_name} 컬럼</li>
 *   <li>Redis 피드백 카운터 키 ({@code feedback:positive:{originalName}})</li>
 *   <li>Redis REGISTERED 플래그 키 ({@code feedback:registered:{originalName}})</li>
 *   <li>alias 후보 Hash 키 ({@code feedback:product-alias:{originalName}})</li>
 * </ul>
 *
 * <h3>REGISTERED 플래그</h3>
 * <p>
 * 등록 성공 후 {@code feedback:registered:{originalName}} 키를 세팅합니다.
 * {@code REFPositiveFeedbackAggregationHandler}가 이 플래그를 확인하여
 * 이미 등록된 제품에 대한 불필요한 DB 집계와 이벤트 발행을 차단합니다.
 * 등록 실패 시에는 플래그를 세팅하지 않아 다음 긍정 피드백에서 자동으로 재시도됩니다.
 * </p>
 *
 * <h3>멱등성 — Step 7 Outbox 재전달 대비 (F4)</h3>
 * <p>
 * Outbox 인프라가 도입되면 같은 이벤트가 at-least-once로 두 번 이상 들어올 수 있습니다.
 * 이 핸들러는 두 단계의 멱등성 보장을 갖춥니다:
 * </p>
 * <ol>
 *   <li><b>handle() 진입 직후 REGISTERED 플래그 사전 체크</b> — 이미 등록 완료된 productName에 대해
 *       GroceryItem 조회/alias 조회/upsert 전체를 스킵하여 DB·Redis 부하 절감</li>
 *   <li><b>upsertProduct 내부 중복 체크</b> — (productName, groceryItemId) 조합으로 중복이면 noop</li>
 * </ol>
 * <p>
 * 두 단계 모두 멱등하므로, Outbox 재전달이나 동시 처리에서도 안전합니다.
 * </p>
 *
 * <h3>비동기 실행</h3>
 * <p>
 * {@code @Async}로 실행되어 긍정 피드백 처리 스레드를 블로킹하지 않습니다.
 * GroceryItem 조회, upsertProduct 등 외부 저장소 접근이 포함되므로
 * 피드백 응답 지연을 방지하기 위해 비동기 처리가 필수입니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 3. (Step 7 F4 멱등성 강화: 2026. 5. 30.)
 * @see com.refridge.core_server.recognition_feedback.infra.event.REFPositiveFeedbackAggregationHandler
 * @see com.refridge.core_server.recognition_feedback.domain.service.REFProductRegistrationPolicy
 * @see REFAliasConfirmationService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductFeedbackAggregationEventHandler {

    private final REFProductLifeCycleService productLifeCycleService;
    private final REFGroceryItemRepository groceryItemRepository;
    private final REFAliasConfirmationService aliasConfirmationService;
    private final StringRedisTemplate redisTemplate;

    /** Product 등록 완료 여부를 나타내는 Redis 플래그 키 접두사 ({@code feedback:registered:{originalName}}) */
    static final String REGISTERED_FLAG_PREFIX = "feedback:registered:";

    /**
     * {@code REFProductFeedbackAggregationEvent}를 수신하여 Product 자동 등록을 처리합니다.
     *
     * <h3>처리 순서</h3>
     * <ol>
     *   <li>{@code groceryItemId} 유효성 검증 — 누락 시 경고 로그 후 종료</li>
     *   <li><b>★ REGISTERED 플래그 사전 체크 — Outbox 재전달 멱등성 가드 (F4)</b></li>
     *   <li>{@code REFGroceryItem} 조회 — 카테고리 ID 확보 목적. 없으면 종료</li>
     *   <li>alias 조회 ({@code alias:confirmed HGET}) — alias 있으면 alias명, 없으면 원본명으로 등록명 결정</li>
     *   <li>{@code upsertProduct()} 호출 — 중복이면 무시, 신규면 INSERT</li>
     *   <li>REGISTERED 플래그 세팅 — 이후 동일 제품에 대한 중복 이벤트 발행 차단</li>
     * </ol>
     *
     * <h3>GroceryItem 조회 이유</h3>
     * <p>
     * {@code REFProduct}는 {@code REFGroceryItemReference}에
     * {@code majorCategoryId}, {@code minorCategoryId}를 비정규화하여 보유합니다.
     * 이벤트에는 {@code groceryItemId}만 포함되므로,
     * 카테고리 ID를 얻기 위해 GroceryItem을 직접 조회합니다.
     * </p>
     *
     * <h3>실패 시 동작</h3>
     * <p>
     * {@code upsertProduct()} 예외 발생 시 REGISTERED 플래그를 세팅하지 않습니다.
     * 플래그가 없으면 {@code REFPositiveFeedbackAggregationHandler}가
     * 다음 긍정 피드백에서 동일 흐름을 재시도하므로 자동 복구됩니다.
     * </p>
     *
     * @param event 긍정 피드백 등록 정책 충족 시 발행되는 집계 이벤트
     *              ({@code productName}, {@code brandName}, {@code groceryItemId}, {@code imageUrl} 포함)
     */
    @Async
    @EventListener
    public void handle(REFProductFeedbackAggregationEvent event) {
        Long groceryItemId = event.groceryItemId();
        String originalProductName = event.productName();

        // groceryItemId 누락 = 파이프라인이 식재료를 매핑하지 못한 케이스
        // Product 등록에 필수 값이므로 등록 불가
        if (groceryItemId == null) {
            log.warn("[Product 자동등록] groceryItemId 누락. productName='{}'", originalProductName);
            return;
        }

        // ── F4 멱등성 가드: REGISTERED 플래그 사전 체크 ───────────────────────
        // Outbox 재전달(at-least-once)로 동일 이벤트가 재유입되는 경우, 이전 처리에서 세팅된
        // 플래그를 확인하여 GroceryItem 조회·alias 조회·upsertProduct 전체를 스킵합니다.
        // 플래그가 없는 경우만 정상 흐름을 타므로 등록 실패 후 자동 재시도 동작도 보존됩니다.
        final String flagKey = REGISTERED_FLAG_PREFIX + originalProductName;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(flagKey))) {
            log.debug("[Product 자동등록] 이미 등록 완료된 productName='{}' — 중복 이벤트 스킵 (멱등 가드).",
                    originalProductName);
            return;
        }

        // GroceryItem 조회 — REFProduct.REFGroceryItemReference에 비정규화할
        // majorCategoryId, minorCategoryId를 확보하기 위해 필요
        REFGroceryItem groceryItem = groceryItemRepository.findById(groceryItemId)
                .orElse(null);

        if (groceryItem == null) {
            log.warn("[Product 자동등록] GroceryItem 없음. groceryItemId={}, productName='{}'",
                    groceryItemId, originalProductName);
            return;
        }

        // ── alias 조회 → 등록 제품명 결정 ────────────────────────
        // alias:confirmed HGET O(1) 조회 — DB 접근 없음
        // alias 있으면: 사용자가 검증한 올바른 제품명으로 Product 등록 (ProductIndex 매칭 정확도 향상)
        // alias 없으면: 파이프라인 원본 정제명으로 등록
        String finalProductName = aliasConfirmationService
                .findConfirmedAlias(originalProductName)
                .orElse(originalProductName);

        if (!finalProductName.equals(originalProductName)) {
            log.info("[Product 자동등록] alias 적용. '{}' → '{}'",
                    originalProductName, finalProductName);
        }

        try {
            // productName + groceryItemId 조합으로 중복 체크 후 upsert
            // 이미 동일 Product가 존재하면 INSERT 없이 조용히 리턴 (멱등성 보장)
            productLifeCycleService.upsertProduct(
                    finalProductName,
                    event.brandName(),
                    groceryItemId,
                    groceryItem.getMajorCategoryId(),
                    groceryItem.getMinorCategoryId()
            );

            // ── REGISTERED 플래그 세팅 ────────────────────────────
            // 원본명 기준으로 세팅 — 피드백 카운터 키(feedback:positive:{originalName})와 일치시켜
            // REFPositiveFeedbackAggregationHandler의 isAlreadyRegistered() 체크가 정확히 동작하게 함.
            // finalProductName(alias명) 기준으로 세팅하면 카운터 키와 불일치하여 플래그가 동작하지 않음.
            redisTemplate.opsForValue().set(flagKey, "1");

            log.info("[Product 자동등록] 완료. originalName='{}', registeredName='{}', groceryItemId={}",
                    originalProductName, finalProductName, groceryItemId);

        } catch (Exception e) {
            // 등록 실패 시 REGISTERED 플래그 미세팅 → 다음 긍정 피드백에서 자동 재시도
            log.error("[Product 자동등록] 실패. productName='{}', groceryItemId={}, 사유: {}",
                    originalProductName, groceryItemId, e.getMessage());
        }
    }
}