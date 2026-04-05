package com.refridge.core_server.recognition_feedback.presentation;

import com.refridge.core_server.recognition_feedback.application.REFReviewAdminService;
import com.refridge.core_server.recognition_feedback.application.REFReviewItemQueryService;
import com.refridge.core_server.recognition_feedback.application.dto.command.REFReviewApproveCommand;
import com.refridge.core_server.recognition_feedback.application.dto.command.REFReviewRejectCommand;
import com.refridge.core_server.recognition_feedback.application.dto.result.REFReviewItemResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자 검수 큐 REST 컨트롤러입니다.
 *
 * <h3>인증/인가</h3>
 * <p>
 * 현재는 인증 없이 누구나 호출 가능합니다.
 * 추후 JWT 토큰 기반 ADMIN 역할 검증으로 보완 예정입니다.
 * </p>
 *
 * <h3>엔드포인트 목록</h3>
 * <pre>
 *   GET  /admin/review                  검수 항목 목록 조회 (상태/유형 필터 + 페이징)
 *   GET  /admin/review/{reviewId}       검수 항목 단건 조회
 *   POST /admin/review/{reviewId}/approve  검수 항목 승인
 *   POST /admin/review/{reviewId}/reject   검수 항목 반려
 *   GET  /admin/review/stats            검수 현황 요약 (PENDING/ML 학습 대기 건수)
 * </pre>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 */
@Slf4j
@RestController
@RequestMapping("/admin/review")
@RequiredArgsConstructor
public class REFReviewAdminController {

    private final REFReviewAdminService reviewAdminService;
    private final REFReviewItemQueryService reviewItemQueryService;

    /**
     * 검수 항목 목록을 조회합니다.
     *
     * @param status 상태 코드 필터 (P=검수대기, A=승인, R=반려, MT=ML학습대기, 없으면 전체)
     * @param type   유형 코드 필터 (EX=비식재료제거, CA=카테고리재분류, GI=신규식재료, 없으면 전체)
     * @param page   페이지 번호 (0부터 시작, 기본값 0)
     * @param size   페이지 크기 (기본값 20)
     */
    @GetMapping
    public ResponseEntity<Page<REFReviewItemResult>> getReviewItems(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                reviewItemQueryService.getReviewItems(status, type, pageable));
    }

    /**
     * 검수 항목 단건을 조회합니다.
     *
     * @param reviewId 검수 항목 ID
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<REFReviewItemResult> getReviewItem(
            @PathVariable Long reviewId) {

        return ResponseEntity.ok(reviewItemQueryService.getReviewItem(reviewId));
    }

    /**
     * 검수 항목을 승인합니다.
     *
     * <h3>요청 본문 예시 (카테고리 재분류)</h3>
     * <pre>
     * {
     *   "adminNote": "정확한 카테고리로 수정 완료",
     *   "origProductName": "해찬들 초고추장 340ml",
     *   "origBrandName": "해찬들"
     * }
     * </pre>
     *
     * <h3>요청 본문 예시 (기타 유형)</h3>
     * <pre>
     * {
     *   "adminNote": "확인 후 승인"
     * }
     * </pre>
     *
     * @param reviewId 승인할 검수 항목 ID
     * @param request  승인 요청 본문
     */
    @PostMapping("/{reviewId}/approve")
    public ResponseEntity<Void> approveReviewItem(
            @PathVariable Long reviewId,
            @RequestBody ReviewApproveRequest request) {

        reviewAdminService.approveReviewItem(
                REFReviewApproveCommand.builder()
                        .reviewId(reviewId)
                        .adminNote(request.adminNote())
                        .origProductName(request.origProductName())
                        .origBrandName(request.origBrandName())
                        .build()
        );
        return ResponseEntity.ok().build();
    }

    /**
     * 검수 항목을 반려합니다.
     *
     * <h3>요청 본문 예시</h3>
     * <pre>
     * {
     *   "adminNote": "식재료가 맞으나 카테고리는 유지"
     * }
     * </pre>
     *
     * @param reviewId 반려할 검수 항목 ID
     * @param request  반려 요청 본문
     */
    @PostMapping("/{reviewId}/reject")
    public ResponseEntity<Void> rejectReviewItem(
            @PathVariable Long reviewId,
            @RequestBody ReviewRejectRequest request) {

        reviewAdminService.rejectReviewItem(
                REFReviewRejectCommand.builder()
                        .reviewId(reviewId)
                        .adminNote(request.adminNote())
                        .build()
        );
        return ResponseEntity.ok().build();
    }

    /**
     * 검수 현황 요약을 조회합니다 (대시보드 배지용).
     *
     * <h3>응답 예시</h3>
     * <pre>
     * {
     *   "pendingCount": 42,
     *   "mlTrainingPendingCount": 7
     * }
     * </pre>
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getReviewStats() {
        return ResponseEntity.ok(Map.of(
                "pendingCount", reviewItemQueryService.getPendingCount(),
                "mlTrainingPendingCount", reviewItemQueryService.getMlTrainingPendingCount()
        ));
    }

    /* ──────────────────── Request Records ──────────────────── */

    /**
     * 승인 요청 본문.
     *
     * @param adminNote       관리자 메모 (nullable)
     * @param origProductName 원본 제품명 (카테고리 재분류 승인 시 필요, nullable)
     * @param origBrandName   원본 브랜드명 (카테고리 재분류 승인 시 필요, nullable)
     */
    public record ReviewApproveRequest(
            String adminNote,
            String origProductName,
            String origBrandName
    ) {
    }

    /**
     * 반려 요청 본문.
     *
     * @param adminNote 반려 사유 (nullable이나 명확한 사유 기재 권장)
     */
    public record ReviewRejectRequest(
            String adminNote
    ) {
    }
}
