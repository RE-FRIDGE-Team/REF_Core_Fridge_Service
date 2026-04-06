package com.refridge.core_server.recognition_feedback.presentation;

import com.refridge.core_server.recognition_feedback.application.REFMLTrainingExportService;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
 *   GET  /admin/review                       검수 항목 목록 조회 (상태/유형 필터 + 페이징)
 *   GET  /admin/review/stats                 검수 현황 요약 (PENDING / ML학습대기 건수)
 *   GET  /admin/review/{reviewId}            검수 항목 단건 조회
 *   POST /admin/review/{reviewId}/approve    검수 항목 승인
 *   POST /admin/review/{reviewId}/reject     검수 항목 반려
 *   GET  /admin/review/ml-training/export    ML 학습 데이터 CSV 내보내기
 *   GET  /admin/review/ml-training/count     ML 학습 대기 건수 조회
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
    private final REFMLTrainingExportService mlTrainingExportService;

    /* ──────────────────── 검수 큐 조회 ──────────────────── */

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

    /* ──────────────────── 승인 / 반려 ──────────────────── */

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

    /* ──────────────────── ML 학습 데이터 내보내기 ──────────────────── */

    /**
     * ML 학습 대기({@code ML_TRAINING_PENDING}) 항목을 CSV 파일로 내보냅니다.
     *
     * <h3>처리 순서</h3>
     * <ol>
     *   <li>ML_TRAINING_PENDING 항목 조회 (최대 {@code limit}건, 기본값 1,000)</li>
     *   <li>CSV 파일 생성 (UTF-8 BOM 포함, Excel 한글 호환)</li>
     *   <li>각 항목 상태를 ML_TRAINING_PENDING → APPROVED로 전환</li>
     *   <li>CSV 파일 다운로드 응답 반환</li>
     * </ol>
     *
     * <h3>파일명</h3>
     * <pre>ml_training_data_{yyyy-MM-dd}.csv</pre>
     *
     * @param limit 최대 내보내기 건수 (기본값 1,000)
     */
    @GetMapping("/ml-training/export")
    public ResponseEntity<byte[]> exportMlTrainingData(
            @RequestParam(defaultValue = "1000") int limit) {

        log.info("[ML 학습 내보내기] 요청. limit={}", limit);

        byte[] csvBytes = mlTrainingExportService.exportAndComplete(limit);

        String filename = "ml_training_data_" + LocalDate.now() + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(csvBytes.length);

        log.info("[ML 학습 내보내기] 응답. filename='{}', size={}bytes",
                filename, csvBytes.length);

        return ResponseEntity.ok().headers(headers).body(csvBytes);
    }

    /**
     * ML 학습 대기 항목 건수를 조회합니다 (내보내기 전 미리보기용).
     *
     * <h3>응답 예시</h3>
     * <pre>
     * {
     *   "mlTrainingPendingCount": 37
     * }
     * </pre>
     */
    @GetMapping("/ml-training/count")
    public ResponseEntity<Map<String, Long>> getMlTrainingPendingCount() {
        return ResponseEntity.ok(Map.of(
                "mlTrainingPendingCount", mlTrainingExportService.countPendingItems()
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
    ) {}

    /**
     * 반려 요청 본문.
     *
     * @param adminNote 반려 사유 (nullable이나 명확한 사유 기재 권장)
     */
    public record ReviewRejectRequest(
            String adminNote
    ) {}
}