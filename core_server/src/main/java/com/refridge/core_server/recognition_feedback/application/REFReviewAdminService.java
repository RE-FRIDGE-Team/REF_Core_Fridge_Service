package com.refridge.core_server.recognition_feedback.application;

import com.refridge.core_server.product_recognition.domain.REFRecognitionDictionaryRepository;
import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.event.REFDictionarySyncedEvent;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import com.refridge.core_server.product_recognition.infra.sync.REFRecognitionDictionaryRedisSync;
import com.refridge.core_server.recognition_feedback.application.dto.command.REFReviewApproveCommand;
import com.refridge.core_server.recognition_feedback.application.dto.command.REFReviewRejectCommand;
import com.refridge.core_server.recognition_feedback.domain.event.REFCategoryReassignmentApprovedEvent;
import com.refridge.core_server.recognition_feedback.domain.review.*;
import com.refridge.core_server.recognition_feedback.infra.event.improvement.REFExclusionRemovalRedisCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 검수 큐 승인/반려 처리 Application Service입니다.
 *
 * <h3>승인 로직 분기</h3>
 * <table border="1" cellpadding="6" style="border-collapse:collapse;">
 *   <thead>
 *     <tr><th>검수 유형</th><th>sourceHandlerName</th><th>처리 결과</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>EXCLUSION_REMOVAL</td>
 *       <td>모두</td>
 *       <td>APPROVED + 비식재료 사전에서 키워드 실제 제거
 *           → REFDictionarySyncedEvent 발행 → Trie 재빌드</td>
 *     </tr>
 *     <tr>
 *       <td>CATEGORY_REASSIGNMENT</td>
 *       <td>모두</td>
 *       <td>APPROVED + REFCategoryReassignmentApprovedEvent 발행
 *           → EventHandler(Async)가 GroceryItem/Product 카테고리 갱신</td>
 *     </tr>
 *     <tr>
 *       <td>NEW_GROCERY_ITEM</td>
 *       <td>MLPrediction</td>
 *       <td>ML_TRAINING_PENDING (Phase 2: CSV 내보내기 후 최종 APPROVED 전환)</td>
 *     </tr>
 *     <tr>
 *       <td>NEW_GROCERY_ITEM</td>
 *       <td>기타</td>
 *       <td>APPROVED</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h3>EXCLUSION_REMOVAL 승인 처리</h3>
 * <p>
 * 기존에는 단순 APPROVED 상태 전환만 했으나,
 * 이제 비식재료 사전({@code REFRecognitionDictionary})에서 해당 키워드를 실제로 제거합니다.
 * 제거 후 Redis Set 동기화 및 Trie 재빌드가 자동으로 트리거됩니다.
 * 반자동화로 이미 AUTO_REMOVED된 항목은 사전에 키워드가 없을 수 있으므로
 * 멱등하게 처리합니다.
 * </p>
 *
 * <h3>인증/인가</h3>
 * <p>
 * 현재는 인증 없이 누구나 호출 가능합니다.
 * 추후 JWT 토큰 기반 ADMIN 역할 검증으로 보완 예정입니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFReviewAdminService {

    private final REFFeedbackReviewItemRepository reviewItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    // [신규 의존성] EXCLUSION_REMOVAL 승인 시 실제 사전 삭제에 필요
    private final REFRecognitionDictionaryRepository dictionaryRepository;
    private final REFRecognitionDictionaryRedisSync redisSync;
    private final REFExclusionRemovalRedisCounter exclusionRemovalRedisCounter;

    /**
     * 검수 항목을 승인합니다.
     *
     * <h3>처리 흐름</h3>
     * <ol>
     *   <li>검수 항목 조회 (없으면 IllegalArgumentException)</li>
     *   <li>검수 유형 기준으로 승인 방식 분기</li>
     *   <li>EXCLUSION_REMOVAL: 비식재료 사전에서 키워드 실제 제거 후 APPROVED</li>
     *   <li>카테고리 재분류 승인 시 REFCategoryReassignmentApprovedEvent 발행</li>
     * </ol>
     *
     * @param command 승인 커맨드
     * @throws IllegalArgumentException 검수 항목을 찾을 수 없는 경우
     * @throws IllegalStateException    PENDING 상태가 아닌 경우
     */
    @Transactional
    public void approveReviewItem(REFReviewApproveCommand command) {
        REFFeedbackReviewItem item = findItemOrThrow(command.reviewId());

        // ── EXCLUSION_REMOVAL: 비식재료 사전 키워드 실제 제거 ────────
        if (item.getReviewType() == REFReviewType.EXCLUSION_REMOVAL) {
            removeExclusionKeyword(item.getTargetValue());
        }

        if (item.getReviewType() == REFReviewType.NEW_GROCERY_ITEM
                && item.isFromMlPrediction()) {
            // MLPrediction 기반 신규 식재료 → ML 학습 데이터로 수집 후 최종 승인
            item.approveForMlTraining(command.adminNote());
            log.info("[관리자 승인] ML 학습 대기 전환. reviewId={}, target='{}'",
                    command.reviewId(), item.getTargetValue());

        } else {
            // 일반 승인 (EXCLUSION_REMOVAL 포함)
            item.approve(command.adminNote());
            log.info("[관리자 승인] 승인 완료. reviewId={}, type={}, target='{}'",
                    command.reviewId(), item.getReviewType(), item.getTargetValue());
        }

        reviewItemRepository.save(item);

        // 카테고리 재분류 승인 → 비동기 카테고리 변경 이벤트 발행
        if (item.getReviewType() == REFReviewType.CATEGORY_REASSIGNMENT) {
            eventPublisher.publishEvent(new REFCategoryReassignmentApprovedEvent(
                    item.getId(),
                    item.getTargetValue(),
                    command.origProductName(),
                    command.origBrandName(),
                    item.getSourceFeedbackId()
            ));
            log.info("[관리자 승인] 카테고리 재분류 이벤트 발행. reviewId={}", command.reviewId());
        }
    }

    /**
     * 검수 항목을 반려합니다.
     *
     * @param command 반려 커맨드
     * @throws IllegalArgumentException 검수 항목을 찾을 수 없는 경우
     * @throws IllegalStateException    PENDING 상태가 아닌 경우
     */
    @Transactional
    public void rejectReviewItem(REFReviewRejectCommand command) {
        REFFeedbackReviewItem item = findItemOrThrow(command.reviewId());
        item.reject(command.adminNote());
        reviewItemRepository.save(item);

        log.info("[관리자 반려] 반려 완료. reviewId={}, type={}, target='{}', 사유='{}'",
                command.reviewId(), item.getReviewType(), item.getTargetValue(),
                command.adminNote());
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    /**
     * 비식재료 사전에서 키워드를 제거하고 Redis 및 Trie를 동기화합니다.
     *
     * <h3>멱등 처리</h3>
     * <p>
     * 반자동화({@code REFExclusionRemovalHandler})로 이미 AUTO_REMOVED된 항목은
     * 사전에 키워드가 없을 수 있습니다. 이 경우 경고 로그만 남기고 정상 처리합니다.
     * </p>
     *
     * @param rejectionKeyword 제거할 비식재료 키워드
     */
    private void removeExclusionKeyword(String rejectionKeyword) {
        REFRecognitionDictionary exclusionDict = dictionaryRepository
                .findByDictType(REFRecognitionDictionaryType.EXCLUSION)
                .orElse(null);

        if (exclusionDict == null) {
            log.error("[관리자 승인] EXCLUSION 사전을 찾을 수 없습니다. keyword='{}'", rejectionKeyword);
            return;
        }

        if (!exclusionDict.hasEntry(rejectionKeyword)) {
            // 반자동화로 이미 제거된 케이스 — 정상 처리
            log.info("[관리자 승인] 키워드가 이미 사전에 없습니다 (자동 제거됨). keyword='{}'",
                    rejectionKeyword);
            exclusionRemovalRedisCounter.resetCounters(rejectionKeyword);
            return;
        }

        // DB entry 제거
        exclusionDict.removeEntry(rejectionKeyword);
        dictionaryRepository.save(exclusionDict);

        // Redis Set 동기화
        redisSync.sync(exclusionDict);

        // Trie 재빌드 트리거
        eventPublisher.publishEvent(new REFDictionarySyncedEvent(REFRecognitionDictionaryType.EXCLUSION));

        // Redis 카운터 초기화
        exclusionRemovalRedisCounter.resetCounters(rejectionKeyword);

        log.info("[관리자 승인] 비식재료 사전 키워드 제거 완료. keyword='{}'", rejectionKeyword);
    }

    private REFFeedbackReviewItem findItemOrThrow(Long reviewId) {
        return reviewItemRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "검수 항목을 찾을 수 없습니다: reviewId=" + reviewId));
    }
}