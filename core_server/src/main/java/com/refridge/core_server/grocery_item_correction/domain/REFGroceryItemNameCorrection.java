package com.refridge.core_server.grocery_item_correction.domain;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

/**
 * 파이프라인이 잘못 인식한 식재료명을 사용자 투표로 교정한 확정 기록입니다.
 *
 * <h3>역할</h3>
 * <p>
 * {@link com.refridge.core_server.product_alias.domain.REFProductNameAlias}가
 * "원본 제품명 → alias 제품명" 교정을 담당하듯,
 * 이 AR은 "원본 식재료명 → 교정 식재료명" 매핑의 확정 이력을 관리합니다.
 * </p>
 *
 * <h3>상태 전이</h3>
 * <pre>
 *   (없음) → CANDIDATE   최초 3중 게이트 도달 시 생성
 *   CANDIDATE → CONFIRMED 3중 게이트 통과 시 자동 확정
 *   CONFIRMED → CANDIDATE 경쟁 후보 재부상 시 재심사 (reopen)
 * </pre>
 *
 * <h3>불변식</h3>
 * <ul>
 *   <li>하나의 {@code originalName}에 대해 레코드는 최대 1건만 존재한다.</li>
 *   <li>상태 전이는 정해진 방향(CANDIDATE→CONFIRMED, CONFIRMED→CANDIDATE)으로만 가능하다.</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 */
@Entity
@Getter
@Table(name = "ref_grocery_item_name_correction",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_correction_original_name",
                        columnNames = "original_name")
        },
        indexes = {
                @Index(name = "idx_correction_status",
                        columnList = "status"),
                @Index(name = "idx_correction_original_confirmed",
                        columnList = "original_name, status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryItemNameCorrection
        extends AbstractAggregateRoot<REFGroceryItemNameCorrection> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "correction_id")
    private Long id;

    /** 파이프라인이 인식한 원본 식재료명 */
    @Column(name = "original_name", nullable = false, length = 200)
    private String originalName;

    /** 사용자 투표로 교정된 식재료명 */
    @Column(name = "corrected_name", nullable = false, length = 200)
    private String correctedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CorrectionStatus status;

    /** 1위 수정본 선택 누적 횟수 */
    @Column(name = "occurrence_count", nullable = false)
    private long occurrenceCount;

    /** 전체 반응 횟수 (긍정 피드백 포함) — Gate 2 비율 계산용 */
    @Column(name = "total_selection_count", nullable = false)
    private long totalSelectionCount;

    @Embedded
    private REFEntityTimeMetaData timeMetaData;

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

    /**
     * CANDIDATE 상태의 교정 기록을 생성합니다.
     * 생성 직후 {@link #confirm()}을 호출하여 CONFIRMED로 전환합니다.
     */
    public static REFGroceryItemNameCorrection createCandidate(
            String originalName,
            String correctedName,
            long occurrenceCount,
            long totalSelectionCount) {

        REFGroceryItemNameCorrection correction = new REFGroceryItemNameCorrection();
        correction.originalName = originalName;
        correction.correctedName = correctedName;
        correction.status = CorrectionStatus.CANDIDATE;
        correction.occurrenceCount = occurrenceCount;
        correction.totalSelectionCount = totalSelectionCount;

        LocalDateTime now = LocalDateTime.now();
        correction.timeMetaData = new REFEntityTimeMetaData(now, now);
        return correction;
    }

    /* ──────────────────── BUSINESS LOGIC ──────────────────── */

    /**
     * CANDIDATE → CONFIRMED 전환.
     * 3중 게이트를 통과한 경우에만 호출됩니다.
     */
    public void confirm() {
        if (this.status != CorrectionStatus.CANDIDATE) {
            throw new IllegalStateException(
                    "CANDIDATE 상태에서만 확정 가능. 현재: " + this.status);
        }
        this.status = CorrectionStatus.CONFIRMED;
    }

    /**
     * CONFIRMED → CANDIDATE 전환 (재심사).
     * 경쟁 후보가 재부상하여 3중 게이트 조건이 더 이상 충족되지 않을 때 호출합니다.
     */
    public void reopen() {
        if (this.status != CorrectionStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "CONFIRMED 상태에서만 재심사 가능. 현재: " + this.status);
        }
        this.status = CorrectionStatus.CANDIDATE;
    }

    /** 횟수 갱신 — 새로운 선택이 누적될 때마다 호출됩니다. */
    public void updateCounts(long occurrenceCount, long totalSelectionCount) {
        this.occurrenceCount = occurrenceCount;
        this.totalSelectionCount = totalSelectionCount;
    }

    /* ──────────────────── QUERY ──────────────────── */

    public boolean isConfirmed() {
        return this.status == CorrectionStatus.CONFIRMED;
    }

    public boolean isCandidate() {
        return this.status == CorrectionStatus.CANDIDATE;
    }

    public double correctionRatio() {
        return totalSelectionCount == 0 ? 0.0
                : (double) occurrenceCount / totalSelectionCount;
    }

    /* ──────────────────── ENUM ──────────────────── */

    public enum CorrectionStatus {
        CANDIDATE,
        CONFIRMED
    }
}
