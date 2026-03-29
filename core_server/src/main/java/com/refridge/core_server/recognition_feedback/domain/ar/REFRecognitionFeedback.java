package com.refridge.core_server.recognition_feedback.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.recognition_feedback.domain.REFRecognitionFeedbackRepository;
import com.refridge.core_server.recognition_feedback.domain.event.REFNegativeFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.event.REFPositiveFeedbackEvent;
import com.refridge.core_server.recognition_feedback.domain.port.REFRecognitionResultQueryPort;
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
 *
 * <h3>생성 전략</h3>
 * <ul>
 *   <li>정상: {@code AFTER_COMMIT} 이벤트 핸들러가 인식 완료 직후 생성</li>
 *   <li>보상: approve/correct 시점에 없으면 {@code findOrCreate()}가 즉시 생성 (Lazy Creation)</li>
 * </ul>
 *
 * <h3>불변식</h3>
 * <ul>
 *   <li>하나의 {@code recognitionId}에 대해 피드백은 최대 1건만 존재한다.</li>
 *   <li>상태 전이는 PENDING에서만 가능하다 (terminal 상태에서는 변경 불가).</li>
 *   <li>{@link REFCorrectionDiff}는 수정(CORRECTED) 시에만 자동 계산되어 저장된다.</li>
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

    @Embedded
    private REFRecognitionReference recognitionReference;

    @Embedded
    private REFRequesterReference requesterReference;

    @Column(name = "status", nullable = false, length = 2)
    @Convert(converter = REFFeedbackStatusConverter.class)
    private REFFeedbackStatus status;

    @Embedded
    private REFOriginalRecognitionSnapshot originalSnapshot;

    @Embedded
    private REFUserCorrectionData userCorrection;

    @Embedded
    private REFCorrectionDiff correctionDiff;

    @Embedded
    private REFEntityTimeMetaData timeMetaData;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "auto_approved", nullable = false)
    private boolean autoApproved;

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

    /** 순수 생성 — 저장 없이 PENDING 상태의 피드백 엔티티만 생성. */
    public static REFRecognitionFeedback create(
            REFRecognitionReference recognitionReference,
            REFRequesterReference requesterReference,
            REFOriginalRecognitionSnapshot snapshot) {

        return new REFRecognitionFeedback(recognitionReference, requesterReference, snapshot);
    }

    /**
     * 피드백을 생성하고 저장합니다 (멱등).
     * 동일 recognitionId에 이미 존재하면 기존 피드백을 반환합니다.
     * AFTER_COMMIT 이벤트 핸들러에서 사용됩니다.
     */
    public static REFRecognitionFeedback createAndSave(
            REFRecognitionReference recognitionReference,
            REFRequesterReference requesterReference,
            REFOriginalRecognitionSnapshot snapshot,
            REFRecognitionFeedbackRepository repository) {

        return repository.findByRecognitionId(recognitionReference.getRecognitionId())
                .orElseGet(() -> repository.save(
                        create(recognitionReference, requesterReference, snapshot)
                ));
    }

    /**
     * recognitionId로 피드백을 조회하되, 없으면 인식 결과를 기반으로 즉시 생성합니다 (Lazy Creation).
     * <p>
     * AFTER_COMMIT 이벤트가 실패한 경우의 보상 경로입니다.
     * approve/correct 호출 전에 이 메서드로 피드백 존재를 보장합니다.
     *
     * @param recognitionId      인식 결과 UUID
     * @param repository         피드백 저장소
     * @param recognitionQueryPort 인식 결과 조회 포트 (Lazy Creation 시 사용)
     * @return 기존 또는 새로 생성된 피드백
     */
    public static REFRecognitionFeedback findOrCreate(
            UUID recognitionId,
            REFRecognitionFeedbackRepository repository,
            REFRecognitionResultQueryPort recognitionQueryPort) {

        return repository.findByRecognitionId(recognitionId)
                .orElseGet(() -> {
                    REFOriginalRecognitionSnapshot snapshot = recognitionQueryPort
                            .findSnapshotByRecognitionId(recognitionId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "인식 결과를 찾을 수 없습니다: " + recognitionId));

                    UUID requesterId = recognitionQueryPort
                            .findRequesterIdByRecognitionId(recognitionId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "인식 결과의 요청자를 찾을 수 없습니다: " + recognitionId));

                    return repository.save(create(
                            REFRecognitionReference.of(recognitionId),
                            REFRequesterReference.of(requesterId),
                            snapshot
                    ));
                });
    }

    /* ──────────────────── BUSINESS LOGIC ──────────────────── */

    /**
     * 사용자가 인식 결과를 승인합니다 (긍정 피드백).
     *
     * @param purchasePrice 구매 가격 (nullable)
     * @throws IllegalStateException PENDING 상태가 아닌 경우
     */
    public void approve(Long purchasePrice) {
        validatePendingStatus();
        completeAsApproved(purchasePrice, false);
    }

    /**
     * 미응답 피드백을 자동 승인 처리합니다 (배치 전용, 멱등).
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
     *   <li>실질적 변경 없음 → APPROVED</li>
     *   <li>실질적 변경 있음 → CORRECTED + diff 저장</li>
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
            completeAsApproved(correctionData.getPurchasePrice(), false);
        } else {
            applyCorrection(correctionData, diff);
        }
    }

    /* ──────────────────── QUERY METHOD ──────────────────── */

    public boolean isApproved() {
        return this.status == REFFeedbackStatus.APPROVED;
    }

    public boolean isCorrected() {
        return this.status == REFFeedbackStatus.CORRECTED;
    }

    public boolean isPending() {
        return this.status.isPending();
    }

    public boolean isAutoApproved() {
        return this.autoApproved;
    }

    public String getCompletedByHandler() {
        return this.originalSnapshot.getCompletedBy();
    }

    public REFOriginalRecognitionSnapshot getOriginalSnapshot() {
        return this.originalSnapshot;
    }

    public UUID getRecognitionIdValue() {
        return this.recognitionReference.getRecognitionId();
    }

    public UUID getRequesterIdValue() {
        return this.requesterReference.getRequesterId();
    }

    /* ──────────────────── INTERNAL ──────────────────── */

    private void completeAsApproved(Long purchasePrice, boolean isAutoApproved) {
        this.status = REFFeedbackStatus.APPROVED;
        this.resolvedAt = LocalDateTime.now();
        this.autoApproved = isAutoApproved;

        if (purchasePrice != null) {
            this.userCorrection = REFUserCorrectionData.priceOnly(purchasePrice);
        }

        registerEvent(new REFPositiveFeedbackEvent(
                this.id, this.recognitionReference,
                this.originalSnapshot, purchasePrice
        ));
    }

    private void applyCorrection(REFUserCorrectionData correctionData, REFCorrectionDiff diff) {
        this.status = REFFeedbackStatus.CORRECTED;
        this.userCorrection = correctionData;
        this.correctionDiff = diff;
        this.resolvedAt = LocalDateTime.now();

        registerEvent(new REFNegativeFeedbackEvent(
                this.id, this.recognitionReference,
                this.originalSnapshot, this.userCorrection, this.correctionDiff
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