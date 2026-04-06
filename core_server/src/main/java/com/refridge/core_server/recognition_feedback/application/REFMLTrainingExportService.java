package com.refridge.core_server.recognition_feedback.application;

import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItem;
import com.refridge.core_server.recognition_feedback.domain.review.REFFeedbackReviewItemRepository;
import com.refridge.core_server.recognition_feedback.domain.review.REFReviewStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ML 모델 학습 데이터 CSV 내보내기 Application Service입니다.
 *
 * <h3>대상 항목</h3>
 * <p>
 * {@link REFReviewStatus#ML_TRAINING_PENDING} 상태인 검수 항목을 조회하여 CSV로 변환합니다.
 * 이 항목들은 {@code MLPrediction} 핸들러에서 인식된 결과에 대해
 * 사용자가 식재료명을 수정하고 관리자가 승인한 케이스입니다.
 * </p>
 *
 * <h3>CSV 컬럼 구성</h3>
 * <pre>
 *   review_id          — 검수 항목 ID (역추적용)
 *   corrected_grocery  — 사용자 수정 식재료명 (targetValue = ML 학습 정답 레이블)
 *   context_detail     — 원본 제품명, 원본 식재료명 등 부가 컨텍스트 (특징 추출용)
 *   occurrence_count   — 동일 수정 누적 횟수 (학습 가중치 참고용)
 *   source_feedback_id — 최초 발생 피드백 ID (역추적용)
 *   created_at         — 생성 시점
 * </pre>
 *
 * <h3>내보내기 후 상태 전환</h3>
 * <p>
 * CSV 생성 성공 후 각 항목의 {@code completeMlTraining()}을 호출하여
 * {@code ML_TRAINING_PENDING → APPROVED}로 전환합니다.
 * 일부 항목 전환 실패 시 해당 항목만 건너뛰고 로그를 남깁니다 (부분 실패 허용).
 * </p>
 *
 * <h3>Spring Batch와의 관계</h3>
 * <p>
 * REST 엔드포인트({@code GET /admin/review/ml-training/export})를 통한 수동 내보내기와
 * Phase 2 Spring Batch 잡을 통한 자동 처리 두 경로 모두에서 이 서비스를 사용합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 * @see com.refridge.core_server.recognition_feedback.infra.batch.REFReviewItemCleanupJobConfig
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class REFMLTrainingExportService {

    private final REFFeedbackReviewItemRepository reviewItemRepository;

    private static final int DEFAULT_EXPORT_LIMIT = 1_000;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String CSV_HEADER =
            "review_id,corrected_grocery,context_detail,occurrence_count," +
                    "source_feedback_id,created_at";

    /**
     * ML 학습 대기 항목을 CSV 바이트 배열로 내보냅니다 (기본 1,000건).
     *
     * @return UTF-8 BOM 포함 CSV 바이트 배열
     * @throws IllegalStateException 내보낼 항목이 없는 경우
     */
    @Transactional
    public byte[] exportAndComplete() {
        return exportAndComplete(DEFAULT_EXPORT_LIMIT);
    }

    /**
     * ML 학습 대기 항목을 CSV 바이트 배열로 내보냅니다 (건수 지정).
     *
     * <h3>처리 순서</h3>
     * <ol>
     *   <li>ML_TRAINING_PENDING 항목 조회 (최대 {@code limit}건)</li>
     *   <li>CSV 파일 생성 (UTF-8 BOM 포함 — Excel 한글 호환)</li>
     *   <li>각 항목 {@code completeMlTraining()} 호출 → APPROVED 전환</li>
     *   <li>CSV 바이트 배열 반환</li>
     * </ol>
     *
     * @param limit 최대 내보내기 건수
     * @return UTF-8 BOM 포함 CSV 바이트 배열
     * @throws IllegalStateException 내보낼 항목이 없는 경우
     */
    @Transactional
    public byte[] exportAndComplete(int limit) {
        List<REFFeedbackReviewItem> items =
                reviewItemRepository.findMlTrainingPendingItems(limit);

        if (items.isEmpty()) {
            throw new IllegalStateException("내보낼 ML 학습 대기 항목이 없습니다.");
        }

        log.info("[ML 학습 내보내기] 시작. 대상 건수={}", items.size());

        byte[] csvBytes = buildCsvBytes(items);

        // ── ML_TRAINING_PENDING → APPROVED 상태 전환 ──────────────
        int completed = 0;
        for (REFFeedbackReviewItem item : items) {
            try {
                item.completeMlTraining();
                reviewItemRepository.save(item);
                completed++;
            } catch (Exception e) {
                log.error("[ML 학습 내보내기] 상태 전환 실패. reviewId={}, 사유: {}",
                        item.getId(), e.getMessage());
            }
        }

        log.info("[ML 학습 내보내기] 완료. 총={}건, 전환성공={}건", items.size(), completed);
        return csvBytes;
    }

    /**
     * ML 학습 대기 항목 수를 조회합니다 (내보내기 전 미리보기용).
     */
    @Transactional(readOnly = true)
    public long countPendingItems() {
        return reviewItemRepository.countByStatus(REFReviewStatus.ML_TRAINING_PENDING);
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    /**
     * 검수 항목 목록을 CSV 바이트 배열로 변환합니다.
     * UTF-8 BOM을 앞에 붙여 Excel에서 한글이 깨지지 않도록 합니다.
     */
    private byte[] buildCsvBytes(List<REFFeedbackReviewItem> items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            // UTF-8 BOM (Excel 한글 호환)
            bos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {
                writer.println(CSV_HEADER);
                for (REFFeedbackReviewItem item : items) {
                    writer.println(toCsvRow(item));
                }
                writer.flush();
            }
            return bos.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("CSV 생성 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 검수 항목 단건을 CSV 행 문자열로 변환합니다.
     * RFC 4180에 따라 쉼표·개행·큰따옴표가 포함된 필드는 큰따옴표로 감쌉니다.
     */
    private String toCsvRow(REFFeedbackReviewItem item) {
        return String.join(",",
                escape(String.valueOf(item.getId())),
                escape(item.getTargetValue()),
                escape(item.getContextDetail()),
                escape(String.valueOf(item.getOccurrenceCount())),
                escape(item.getSourceFeedbackId() != null
                        ? item.getSourceFeedbackId().toString() : ""),
                escape(item.getTimeMetaData() != null
                        ? item.getTimeMetaData().getCreatedAt().format(DATE_FORMATTER) : "")
        );
    }

    /**
     * CSV 필드 값을 RFC 4180 규칙에 따라 이스케이프합니다.
     */
    private String escape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}