package com.refridge.core_server.recognition_feedback.domain.review;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import com.refridge.core_server.recognition_feedback.infra.converter.REFReviewStatusConverter;
import com.refridge.core_server.recognition_feedback.infra.converter.REFReviewTypeConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자 검수 큐에 적재되는 검수 대기 항목입니다.
 * <p>
 * 자동 반영하면 위험도가 높은 피드백(비식재료 사전 수정, 카테고리 재분류, 신규 식재료 등)을
 * 관리자가 검토한 뒤 승인/거부할 수 있도록 합니다.
 *
 * <h3>생명주기</h3>
 * <pre>
 *   PENDING ──→ APPROVED  (관리자 승인 → 실제 반영 이벤트 발행)
 *          └──→ REJECTED  (관리자 거부 → 반영하지 않음)
 * </pre>
 *
 * <h3>중복 방지</h3>
 * {@code reviewType + targetValue} 조합으로 유니크 — 동일한 검수 요청이 중복 적재되지 않습니다.
 * 대신 누적 횟수({@code occurrenceCount})가 증가합니다.
 */
@Entity
@SuppressWarnings("NullableProblems")
@Table(name = "ref_feedback_review_item",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_type_target",
                        columnNames = {"review_type", "target_value"}
                )
        },
        indexes = {
                @Index(name = "idx_review_status", columnList = "status"),
                @Index(name = "idx_review_type_status", columnList = "review_type, status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFFeedbackReviewItem extends AbstractAggregateRoot<REFFeedbackReviewItem> {

    @Id
    @Getter
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    /** 검수 유형 (비식재료 제거, 카테고리 재분류, 신규 식재료, 브랜드 추가) */
    @Column(name = "review_type", nullable = false, length = 2)
    @Convert(converter = REFReviewTypeConverter.class)
    private REFReviewType reviewType;

    /** 검수 대상 값 (e.g. 제거할 비식재료 키워드, 추가할 브랜드명, 신규 식재료명) */
    @Column(name = "target_value", nullable = false, length = 200)
    private String targetValue;

    /** 부가 컨텍스트 정보 (e.g. 원본 제품명, 카테고리 경로, completedBy 핸들러 등) */
    @Column(name = "context_detail", length = 500)
    private String contextDetail;

    /** 이 검수 요청을 최초 발생시킨 피드백 ID */
    @Column(name = "source_feedback_id")
    private UUID sourceFeedbackId;

    /** 동일 검수 요청이 누적된 횟수 — 관리자가 우선순위 판단에 활용 */
    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    /** 현재 검수 상태 */
    @Column(name = "status", nullable = false, length = 2)
    @Convert(converter = REFReviewStatusConverter.class)
    private REFReviewStatus status;

    @Embedded
    private REFEntityTimeMetaData timeMetaData;

    /** 관리자 처리 시점 */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 관리자 메모 (거부 사유 등) */
    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @PrePersist
    protected void onCreate() {
        if (timeMetaData == null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = new REFEntityTimeMetaData(now, now);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (timeMetaData != null) {
            timeMetaData = timeMetaData.updateModifiedAt(LocalDateTime.now());
        }
    }

    /* ──────────────────── FACTORY METHOD ──────────────────── */

    private REFFeedbackReviewItem(REFReviewType reviewType,
                                  String targetValue,
                                  String contextDetail,
                                  UUID sourceFeedbackId) {
        this.reviewType = reviewType;
        this.targetValue = targetValue;
        this.contextDetail = contextDetail;
        this.sourceFeedbackId = sourceFeedbackId;
        this.occurrenceCount = 1;
        this.status = REFReviewStatus.PENDING;

        LocalDateTime now = LocalDateTime.now();
        this.timeMetaData = new REFEntityTimeMetaData(now, now);
    }

    /**
     * 검수 항목을 생성합니다.
     *
     * @param reviewType      검수 유형
     * @param targetValue     검수 대상 값
     * @param contextDetail   부가 컨텍스트 (nullable)
     * @param sourceFeedbackId 최초 발생 피드백 ID
     */
    public static REFFeedbackReviewItem create(REFReviewType reviewType,
                                               String targetValue,
                                               String contextDetail,
                                               UUID sourceFeedbackId) {
        return new REFFeedbackReviewItem(reviewType, targetValue, contextDetail, sourceFeedbackId);
    }

    /**
     * 동일한 검수 요청이 중복 적재된 경우 누적 횟수를 증가시킵니다 (멱등 대응).
     * 새 피드백에서 동일 요청이 발생했을 때 Repository에서 기존 항목을 찾아 호출합니다.
     */
    public void incrementOccurrence() {
        this.occurrenceCount++;
    }

    /* ──────────────────── BUSINESS LOGIC ──────────────────── */

    /**
     * 관리자가 검수 항목을 승인합니다.
     * 승인 후 실제 반영(사전 수정, 카테고리 변경 등)은 이벤트 핸들러가 처리합니다.
     *
     * @param adminNote 관리자 메모 (nullable)
     */
    public void approve(String adminNote) {
        validatePending();
        this.status = REFReviewStatus.APPROVED;
        this.adminNote = adminNote;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 관리자가 검수 항목을 거부합니다.
     *
     * @param adminNote 거부 사유
     */
    public void reject(String adminNote) {
        validatePending();
        this.status = REFReviewStatus.REJECTED;
        this.adminNote = adminNote;
        this.resolvedAt = LocalDateTime.now();
    }

    /* ──────────────────── QUERY ──────────────────── */

    public boolean isPending() {
        return this.status.isPending();
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    private void validatePending() {
        if (!this.status.isPending()) {
            throw new IllegalStateException(
                    String.format("PENDING 상태에서만 처리 가능합니다. 현재 상태: %s", this.status.getKorCode()));
        }
    }
}