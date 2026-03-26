package com.refridge.core_server.recognition_feedback.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
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
import java.util.UUID;

/**
 * 인식 결과에 대한 사용자 피드백을 관리하는 Aggregate Root입니다.
 *
 * <h3>생명주기</h3>
 * <pre>
 *   PENDING ──→ APPROVED   (사용자 승인 또는 미응답 자동 승인)
 *          └──→ CORRECTED  (사용자가 인식 결과 수정)
 * </pre>
 * 미응답 시에도 APPROVED로 처리합니다 — 사용자가 별도 수정 없이 넘어간 경우
 * 인식 결과에 이의가 없다고 판단합니다.
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
 *   <li>{@link REFPositiveFeedbackEvent} — 승인 시 발행 (자동 승인 포함)</li>
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
    private REFRecognitionReference recognitionReference;

    /** 피드백을 생성한 사용자 */
    @Embedded
    private REFRequesterReference requesterReference;

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

    /** 피드백 처리 완료 시점 (승인 또는 수정) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 자동 승인 여부 — 배치에 의해 자동 승인된 경우 true */
    @Column(name = "auto_approved", nullable = false)
    private boolean autoApproved;

    /** 낙관적 잠금 — 동시 수정 방지 */
    @Version
    private int version;

    /* ──────────────────── JPA 라이프사이클 ──────────────────── */

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

    /* ──────────────────── INTERNAL CONSTRUCTOR ──────────────────── */

    private REFRecognitionFeedback(REFRecognitionReference recognitionReference,
                                   REFRequesterReference requesterReference,
                                   REFOriginalRecognitionSnapshot snapshot) {
        this.id = REFRecognitionFeedbackId.generate();
        this.recognitionReference = recognitionReference;
        this.requesterReference = requesterReference;
        this.status = REFFeedbackStatus.PENDING;
        this.originalSnapshot = snapshot;
        this.userCorrection = null;
        this.correctionDiff = null;
        this.autoApproved = false;

        LocalDateTime now = LocalDateTime.now();
        this.timeMetaData = new REFEntityTimeMetaData(now, now);
    }

    /* ──────────────────── CREATION FACTORY METHOD ──────────────────── */

    /**
     * PENDING 상태의 피드백 엔티티를 생성합니다.
     * 저장은 포함하지 않습니다 — 중복 체크 없이 순수 생성만 수행.
     */
    public static REFRecognitionFeedback create(
            REFRecognitionReference recognitionReference,
            REFRequesterReference requesterReference,
            REFOriginalRecognitionSnapshot snapshot) {

        return new REFRecognitionFeedback(recognitionReference, requesterReference, snapshot);
    }

    /**
     * PENDING 상태의 피드백을 생성하고 저장합니다.
     * <p>
     * 동일 recognitionId에 대해 이미 피드백이 존재하면 기존 피드백을 반환합니다 (멱등).
     * recognize() 내부에서 동기적으로 호출됩니다.
     *
     * @param recognitionReference 인식 결과 참조
     * @param requesterReference   요청자 참조
     * @param snapshot             인식 결과 스냅샷
     * @param repository           피드백 저장소
     * @return 생성되었거나 이미 존재하는 피드백
     */
    public static REFRecognitionFeedback createAndSave(
            REFRecognitionReference recognitionReference,
            REFRequesterReference requesterReference,
            REFOriginalRecognitionSnapshot snapshot,
            REFRecognitionFeedbackRepository repository) {

        return repository.findByRecognitionId(recognitionReference.getRecognitionId())
                .orElseGet(() -> {
                    REFRecognitionFeedback feedback = create(recognitionReference, requesterReference, snapshot);
                    return repository.save(feedback);
                });
    }

    /* ──────────────────── BUSINESS LOGIC ──────────────────── */

    /**
     * 사용자가 인식 결과를 승인합니다 (긍정 피드백).
     * <p>
     * 승인 시에도 구매 가격을 입력할 수 있습니다.
     * {@link REFPositiveFeedbackEvent}가 발행됩니다.
     *
     * @param purchasePrice 구매 가격 (nullable)
     * @throws IllegalStateException PENDING 상태가 아닌 경우
     */
    public void approve(Long purchasePrice) {
        validatePendingStatus();
        completeAsApproved(purchasePrice, false);
    }

    /**
     * 미응답 피드백을 자동 승인 처리합니다 (배치 전용).
     * <p>
     * 사용자가 별도 수정 없이 넘어간 경우 인식 결과에 이의가 없다고 판단합니다.
     * {@code autoApproved = true}로 마킹되어 일반 승인과 구분됩니다.
     * <p>
     * 이미 APPROVED/CORRECTED인 경우 아무 동작도 하지 않습니다 (멱등).
     */
    public void autoApprove() {
        if (!this.status.isPending()) {
            return;
        }
        completeAsApproved(null, true);
    }

    /**
     * 사용자가 수정 폼을 제출했을 때 호출합니다.
     * <p>
     * 원본 스냅샷과 수정 데이터의 diff를 직접 계산하여 실질적 변경 여부를 판단합니다:
     * <ul>
     *   <li>실질적 변경 없음 (가격만 입력 등) → APPROVED 처리</li>
     *   <li>실질적 변경 있음 → CORRECTED 처리 + diff 저장</li>
     * </ul>
     *
     * @param correctionData 사용자 수정 데이터
     * @throws IllegalStateException    PENDING 상태가 아닌 경우
     * @throws IllegalArgumentException correctionData가 null인 경우
     */
    public void resolveWithCorrection(REFUserCorrectionData correctionData) {
        validatePendingStatus();
        validateCorrectionNotNull(correctionData);

        REFCorrectionDiff diff = REFCorrectionDiff.calculate(this.originalSnapshot, correctionData);

        if (diff.hasNoChanges()) {
            // 실질적 변경 없음 → 승인 (가격이 있으면 함께 저장)
            completeAsApproved(correctionData.getPurchasePrice(), false);
        } else {
            // 실질적 변경 있음 → 수정
            applyCorrection(correctionData, diff);
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

    /** 배치에 의해 자동 승인되었는지 */
    public boolean isAutoApproved() {
        return this.autoApproved;
    }

    /** 어느 핸들러에서 인식이 완료되었는지 */
    public String getCompletedByHandler() {
        return this.originalSnapshot.getCompletedBy();
    }

    /** 인식 결과 스냅샷 */
    public REFOriginalRecognitionSnapshot getOriginalSnapshot() {
        return this.originalSnapshot;
    }

    /** 인식 결과 ID 값 */
    public UUID getRecognitionIdValue() {
        return this.recognitionReference.getRecognitionId();
    }

    /** 요청자 ID 값 */
    public UUID getRequesterIdValue() {
        return this.requesterReference.getRequesterId();
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    /**
     * APPROVED 상태로 전이하는 공통 로직.
     * 사용자 승인, 자동 승인, 실질적 변경 없는 수정 폼 제출 모두 이 메서드를 통해 처리.
     */
    private void completeAsApproved(Long purchasePrice, boolean isAutoApproved) {
        this.status = REFFeedbackStatus.APPROVED;
        this.resolvedAt = LocalDateTime.now();
        this.autoApproved = isAutoApproved;

        if (purchasePrice != null) {
            this.userCorrection = REFUserCorrectionData.priceOnly(purchasePrice);
        }

        registerEvent(new REFPositiveFeedbackEvent(
                this.id,
                this.recognitionReference,
                this.originalSnapshot,
                purchasePrice
        ));
    }

    /**
     * CORRECTED 상태로 전이. 이미 계산된 diff를 저장하고 이벤트 발행.
     * resolveWithCorrection()에서만 호출됩니다.
     */
    private void applyCorrection(REFUserCorrectionData correctionData, REFCorrectionDiff diff) {
        this.status = REFFeedbackStatus.CORRECTED;
        this.userCorrection = correctionData;
        this.correctionDiff = diff;
        this.resolvedAt = LocalDateTime.now();

        registerEvent(new REFNegativeFeedbackEvent(
                this.id,
                this.recognitionReference,
                this.originalSnapshot,
                this.userCorrection,
                this.correctionDiff
        ));
    }

    private void validatePendingStatus() {
        if (!this.status.isPending()) {
            throw new IllegalStateException(
                    String.format("PENDING 상태에서만 처리 가능합니다. 현재 상태: %s", this.status.getKorCode()));
        }
    }

    private void validateCorrectionNotNull(REFUserCorrectionData correctionData) {
        if (correctionData == null) {
            throw new IllegalArgumentException("수정 데이터는 필수입니다.");
        }
    }
}