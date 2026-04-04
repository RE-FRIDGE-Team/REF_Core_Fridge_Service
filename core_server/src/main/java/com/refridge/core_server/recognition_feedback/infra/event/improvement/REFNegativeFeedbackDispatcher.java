package com.refridge.core_server.recognition_feedback.infra.event.improvement;

import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.REFCorrectionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h3> 부정 피드백 디스패쳐 </h3>
 * 부정 피드백 이벤트를 구독하고, 변경된 각 필드 유형({@link REFCorrectionType})에
 * 대응하는 {@link REFImprovementActionHandler}에 개선 작업을 위임하는 디스패처입니다.
 *
 * <h3>부정 피드백이란</h3>
 * <p>
 * 사용자가 5단계 인식 파이프라인 결과에서 하나 이상의 필드를 수정한 경우입니다.
 * 수정 없이 승인한 경우는 긍정 피드백({@code REFPositiveFeedbackAggregationHandler})으로 처리됩니다.
 * </p>
 *
 * <h3>변경 가능한 필드 유형 및 담당 핸들러</h3>
 * <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;">
 *   <thead>
 *     <tr style="background:#000000;">
 *       <th>REFCorrectionType</th>
 *       <th>담당 핸들러</th>
 *       <th>처리 방식</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td><b>PRODUCT_NAME</b><br>정제 제품명</td>
 *       <td>{@link REFProductNameAliasHandler}</td>
 *       <td>Redis 3중 게이트 → alias 자동 확정</td>
 *     </tr>
 *     <tr>
 *       <td><b>BRAND</b><br>브랜드명</td>
 *       <td>{@link REFBrandImprovementHandler}</td>
 *       <td>Redis 카운터 → 브랜드 사전 자동 반영</td>
 *     </tr>
 *     <tr>
 *       <td><b>CATEGORY</b><br>카테고리</td>
 *       <td>{@link REFCategoryReassignmentHandler}</td>
 *       <td>검수 큐 적재 → 관리자 승인 → GroceryItem · Product 전파</td>
 *     </tr>
 *     <tr>
 *       <td><b>GROCERY_ITEM</b><br>식재료명</td>
 *       <td>{@link REFGroceryItemMappingHandler}</td>
 *       <td>어느 핸들러에서 처리 완료되었는지에 따라 다른 핸들링</td>
 *     </tr>
 *     <tr>
 *       <td><b>EXCLUSION</b><br>비식재료 키워드</td>
 *       <td>{@link REFExclusionRemovalHandler}</td>
 *       <td>검수 큐 적재 → 관리자 승인</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>핸들러 자동 등록 (Strategy + DI)</h3>
 * <p>
 * {@code REFImprovementActionHandler}를 구현한 모든 빈이 생성자 주입으로 수집됩니다.
 * 각 핸들러가 {@code supportedType()}으로 자신이 처리할 {@link REFCorrectionType}을 선언하면
 * {@link EnumMap}에 자동으로 등록됩니다. 새 핸들러 추가 시 이 클래스를 수정할 필요가 없습니다.
 * </p>
 *
 * <h3>복수 필드 동시 변경 처리</h3>
 * <p>
 * 하나의 부정 피드백에서 여러 필드가 동시에 변경된 경우 (예: 제품명 + 브랜드 동시 수정),
 * {@code REFCorrectionDiff}의 {@code changedFields} Set에 포함된 각 유형에 대해
 * 대응하는 핸들러가 독립적으로 순차 실행됩니다.
 * 하나의 핸들러 실패가 나머지 핸들러 실행에 영향을 주지 않습니다.
 * </p>
 *
 * <h3>트랜잭션 경계</h3>
 * <p>
 * {@code @TransactionalEventListener(AFTER_COMMIT)}으로 동작합니다.
 * 피드백 AR 저장 트랜잭션이 완전히 커밋된 이후에만 실행되므로,
 * 핸들러 내부에서 DB 집계 시 해당 피드백이 반드시 포함됩니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 3.
 * @see REFImprovementActionHandler
 * @see REFCorrectionType
 * @see REFNegativeFeedbackEvent
 */
@Slf4j
@Component
public class REFNegativeFeedbackDispatcher {

    /**
     * REFCorrectionType → 핸들러 매핑 테이블.
     * EnumMap을 사용하여 enum 기반 조회 성능을 최적화합니다.
     * (HashMap 대비 배열 기반 조회, O(1) 보장)
     */
    private final Map<REFCorrectionType, REFImprovementActionHandler> handlerMap;

    /**
     * Spring이 주입하는 {@link REFImprovementActionHandler} 구현체 목록을 받아
     * {@link EnumMap}에 등록합니다.
     *
     * <p>
     * 각 핸들러는 {@code supportedType()}으로 자신이 처리할 {@link REFCorrectionType}을 선언합니다.
     * 동일한 타입에 두 개 이상의 핸들러가 등록되면 마지막 등록 핸들러가 덮어씁니다.
     * </p>
     *
     * @param handlers Spring이 수집한 모든 {@link REFImprovementActionHandler} 빈 목록
     */
    public REFNegativeFeedbackDispatcher(List<REFImprovementActionHandler> handlers) {
        this.handlerMap = new EnumMap<>(REFCorrectionType.class);

        // supportedType()을 키로, 핸들러 자신을 값으로 등록.
        // 새 핸들러는 REFImprovementActionHandler를 구현하고 @Component만 붙이면 자동 등록됨.
        handlers.forEach(h -> this.handlerMap.put(h.supportedType(), h));

        log.info("[FeedbackDispatcher] 등록된 개선 핸들러: {}개 — {}",
                handlerMap.size(), handlerMap.keySet());
    }

    /**
     * 부정 피드백 이벤트를 수신하여 변경된 각 필드 유형에 대응하는 핸들러에 위임합니다.
     *
     * <h3>실행 순서</h3>
     * <ol>
     *   <li>{@code REFCorrectionDiff}에서 변경된 필드 유형({@link REFCorrectionType}) Set 추출</li>
     *   <li>변경 없으면 즉시 리턴</li>
     *   <li>각 유형에 대응하는 핸들러를 조회하여 독립적으로 실행</li>
     *   <li>핸들러 미등록 유형은 로깅 후 스킵</li>
     *   <li>개별 핸들러 예외는 catch하여 나머지 핸들러에 영향 주지 않음</li>
     * </ol>
     *
     * @param event 부정 피드백 도메인 이벤트 (snapshot, correction, diff, feedbackId 포함)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(REFNegativeFeedbackEvent event) {
        // REFCorrectionDiff가 계산한 실제 변경 필드 유형 Set.
        // 변경되지 않은 필드는 포함되지 않으며, 이는 곧 묵시적 긍정으로 간주될 수 있음.
        // TODO : 향후 묵시적 긍정 피드백 활용 설계 시 이 Set의 여집합을 활용
        Set<REFCorrectionType> changedFields = event.diff().getChangedFields();

        if (changedFields.isEmpty()) {
            // 부정 피드백인데 changedFields가 비어있는 경우는 정상적으로 발생하지 않아야 함
            log.debug("[FeedbackDispatcher] 변경 필드 없음. feedbackId={}", event.feedbackId().getValue());
            return;
        }

        log.info("[FeedbackDispatcher] 부정 피드백 처리 시작. feedbackId={}, changes={}",
                event.feedbackId().getValue(), changedFields);

        for (REFCorrectionType type : changedFields) {
            // EnumMap O(1) 조회 — 해당 유형에 등록된 핸들러 탐색
            REFImprovementActionHandler handler = handlerMap.get(type);

            if (handler == null) {
                // 핸들러가 아직 구현되지 않은 유형 (미래 확장 유형 포함)
                log.debug("[FeedbackDispatcher] 핸들러 미등록 유형 스킵: {}", type);
                continue;
            }

            try {
                handler.handle(event);
                log.info("[FeedbackDispatcher] 개선 액션 완료. type={}, feedbackId={}",
                        type, event.feedbackId().getValue());
            } catch (Exception e) {
                // 핸들러 단위로 예외를 격리하여 다른 유형의 개선 처리가 중단되지 않도록 함.
                // 예: 브랜드 핸들러 Redis 장애가 제품명 alias 처리에 영향 주지 않음.
                log.error("[FeedbackDispatcher] 개선 액션 실패. type={}, feedbackId={}, 사유: {}",
                        type, event.feedbackId().getValue(), e.getMessage());
            }
        }
    }
}