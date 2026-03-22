package com.refridge.core_server.recognition_feedback.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionId;
import com.refridge.core_server.product_recognition.domain.vo.REFRequesterId;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.event.REFPositiveFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.vo.*;
import com.refridge.core_server.recognition_feedback.infra.converter.REFFeedbackStatusConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

/**
 * 인식 결과에 대한 사용자 피드백을 관리하는 Aggregate Root입니다.
 *
 * <h3>생명주기</h3>
 * <pre>
 *   PENDING ──→ APPROVED  (긍정 피드백: 사용자가 인식 결과 승인)
 *          ├──→ CORRECTED (부정 피드백: 사용자가 인식 결과 수정)
 *          └──→ EXPIRED   (배치: 일정 기간 미응답)
 * </pre>
 *
 * <h3>불변식</h3>
 * <ul>
 *   <li>하나의 {@code recognitionId}에 대해 피드백은 최대 1건만 존재한다.</li>
 *   <li>상태 전이는 PENDING에서만 가능하다 (terminal 상태에서는 변경 불가).</li>
 *   <li>{@link REFCorrectionDiff}는 수정(CORRECTED) 시에만 자동 계산되어 저장된다.</li>
 * </ul>
 *
 * <h3>도메인 이벤트</h3>
 * <ul>
 *   <li>{@link REFPositiveFeedbackEvent} — 승인 시 발행</li>
 *   <li>{@link REFNegativeFeedbackEvent} — 수정 시 발행</li>
 * </ul>
 */
@Entity
@SuppressWarnings("NullableProblems")
@Table(name = "ref_recognition_feedback",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_feedback_recognition_id",
                        columnNames = "recognition_id"
                )
        },
        indexes = {
                @Index(name = "idx_feedback_requester_status",
                        columnList = "requester_id, status"),
                @Index(name = "idx_feedback_status_created",
                        columnList = "status, created_at"),
                @Index(name = "idx_feedback_orig_product_name",
                        columnList = "orig_product_name, status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRecognitionFeedback extends AbstractAggregateRoot<REFRecognitionFeedback> {

    @EmbeddedId
    @Getter
    private REFRecognitionFeedbackId id;

    /** 연관된 인식 결과 참조 (1:1) */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "recognition_id", nullable = false))
    private REFRecognitionId recognitionId;

    /** 피드백을 생성한 사용자 */
    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "requester_id", nullable = false))
    private REFRequesterId requesterId;

    /** 현재 피드백 상태 */
    @Column(name = "status", nullable = false, length = 2)
    @Convert(converter = REFFeedbackStatusConverter.class)
    private REFFeedbackStatus status;

    /** 인식 시점의 결과 스냅샷 (불변) */
    @Embedded
    private REFOriginalRecognitionSnapshot originalSnapshot;

    /** 사용자 수정 데이터 (CORRECTED 또는 가격만 입력한 APPROVED 시) */
    @Embedded
    private REFUserCorrectionData userCorrection;

    /** 원본 vs 수정의 차이 (CORRECTED 시에만 non-null) */
    @Embedded
    private REFCorrectionDiff correctionDiff;

    /** 엔티티 시간 메타데이터 */
    @Embedded
    private REFEntityTimeMetaData timeMetaData;

    /** 승인 시점 */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 수정 시점 */
    @Column(name = "corrected_at")
    private LocalDateTime correctedAt;

    /** 낙관적 잠금 — 동시 수정 방지 */
    @Version
    private int version;

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

    /**
     * 인식 완료 직후 PENDING 상태의 피드백을 생성합니다.
     * <p>
     * recognize() 내부에서 동기적으로 호출되어 feedbackId를 응답에 포함시킵니다.
     *
     * @param recognitionId 연관된 인식 결과 ID
     * @param requesterId   요청 사용자 ID
     * @param snapshot      인식 결과 스냅샷
     * @return PENDING 상태의 피드백 AR
     */
    public static REFRecognitionFeedback createPending(
            REFRecognitionId recognitionId,
            REFRequesterId requesterId,
            REFOriginalRecognitionSnapshot snapshot) {

        REFRecognitionFeedback feedback = new REFRecognitionFeedback();
        feedback.id = REFRecognitionFeedbackId.generate();
        feedback.recognitionId = recognitionId;
        feedback.requesterId = requesterId;
        feedback.status = REFFeedbackStatus.PENDING;
        feedback.originalSnapshot = snapshot;
        feedback.userCorrection = null;
        feedback.correctionDiff = null;

        LocalDateTime now = LocalDateTime.now();
        feedback.timeMetaData = new REFEntityTimeMetaData(now, now);

        return feedback;
    }

    /* ──────────────────── BUSINESS LOGIC ──────────────────── */

    /**
     * 사용자가 인식 결과를 승인합니다 (긍정 피드백).
     * <p>
     * 승인 시에도 구매 가격을 입력할 수 있습니다.
     * {@link REFPositiveFeedbackEvent}가 발행되어 집계 및 Product 자동 등록에 활용됩니다.
     *
     * @param purchasePrice 구매 가격 (nullable)
     * @throws IllegalStateException PENDING 상태가 아닌 경우
     */
    public void approve(Long purchasePrice) {
        validatePendingStatus();

        this.status = REFFeedbackStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();

        if (purchasePrice != null) {
            this.userCorrection = REFUserCorrectionData.priceOnly(purchasePrice);
        }

        registerEvent(new REFPositiveFeedbackEvent(
                this.id,
                this.recognitionId,
                this.originalSnapshot,
                purchasePrice
        ));
    }

    /**
     * 사용자가 인식 결과를 수정합니다 (부정 피드백).
     * <p>
     * 원본 스냅샷과 수정 데이터의 차이가 자동 계산되어 {@link REFCorrectionDiff}로 저장됩니다.
     * {@link REFNegativeFeedbackEvent}가 발행되어 사전 보강 등 개선 액션에 활용됩니다.
     *
     * @param correction 사용자 수정 데이터
     * @throws IllegalStateException    PENDING 상태가 아닌 경우
     * @throws IllegalArgumentException correction이 null인 경우
     */
    public void correct(REFUserCorrectionData correction) {
        validatePendingStatus();
        validateCorrectionNotNull(correction);

        this.status = REFFeedbackStatus.CORRECTED;
        this.userCorrection = correction;
        this.correctionDiff = REFCorrectionDiff.calculate(this.originalSnapshot, correction);
        this.correctedAt = LocalDateTime.now();

        registerEvent(new REFNegativeFeedbackEvent(
                this.id,
                this.recognitionId,
                this.originalSnapshot,
                this.userCorrection,
                this.correctionDiff
        ));
    }

    /**
     * 미응답 피드백을 만료 처리합니다 (배치 전용).
     * <p>
     * PENDING 상태인 경우에만 APPROVED로 전이합니다.
     * 이미 APPROVED/CORRECTED인 경우 아무 동작도 하지 않습니다.
     */
    public void expire() {
        if (this.status.isPending()) {
            this.status = REFFeedbackStatus.APPROVED;
        }
    }

    /* ──────────────────── QUERY METHOD ──────────────────── */

    /** 긍정 피드백 여부 */
    public boolean isApproved() {
        return this.status == REFFeedbackStatus.APPROVED;
    }

    /** 부정 피드백 여부 */
    public boolean isCorrected() {
        return this.status == REFFeedbackStatus.CORRECTED;
    }

    /** 아직 사용자 응답을 기다리는 중인지 */
    public boolean isPending() {
        return this.status.isPending();
    }

    /** 어느 핸들러에서 인식이 완료되었는지 */
    public String getCompletedByHandler() {
        return this.originalSnapshot.getCompletedBy();
    }

    /* ──────────────────── VALIDATION ──────────────────── */

    private void validatePendingStatus() {
        if (!this.status.isPending()) {
            throw new IllegalStateException(
                    String.format("PENDING 상태에서만 처리 가능합니다. 현재 상태: %s", this.status.getKorCode()));
        }
    }

    private void validateCorrectionNotNull(REFUserCorrectionData correction) {
        if (correction == null) {
            throw new IllegalArgumentException("수정 데이터는 필수입니다.");
        }
    }
}