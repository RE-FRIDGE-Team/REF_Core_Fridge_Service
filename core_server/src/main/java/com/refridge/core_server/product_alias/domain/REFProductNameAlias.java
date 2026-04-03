package com.refridge.core_server.product_alias.domain;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

/**
 * 제품명 alias 집계 루트입니다.
 * <p>
 * 사용자들이 파이프라인 인식 결과를 수정한 이력이 임계값을 충족하면
 * "원본 정제명 → alias명" 매핑이 CONFIRMED 상태로 저장됩니다.
 * 이후 파싱 파이프라인은 이 테이블을 참조하여 refinedText를 alias로 자동 교체합니다.
 *
 * <h3>상태 전이</h3>
 * <pre>
 *   (없음) → CANDIDATE  최초 임계값 도달 시 생성
 *   CANDIDATE → CONFIRMED  3중 게이트 통과 시 자동 확정
 *   CONFIRMED → CANDIDATE  경쟁 후보 재부상 시 재심사 (reopen)
 * </pre>
 */
@Entity
@Getter
@Table(name = "ref_product_name_alias",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alias_original_name", columnNames = "original_name")
        },
        indexes = {
                @Index(name = "idx_alias_status", columnList = "status"),
                @Index(name = "idx_alias_original_confirmed", columnList = "original_name, status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFProductNameAlias extends AbstractAggregateRoot<REFProductNameAlias> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alias_id")
    private Long id;

    /** 파이프라인이 정제한 원본 제품명 */
    @Column(name = "original_name", nullable = false, length = 200)
    private String originalName;

    /** 사용자들이 가장 많이 선택한 수정 제품명 */
    @Column(name = "alias_name", nullable = false, length = 200)
    private String aliasName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AliasStatus status;

    /** 1위 수정본 선택 누적 횟수 */
    @Column(name = "occurrence_count", nullable = false)
    private long occurrenceCount;

    /** 전체 선택 횟수 (모든 수정본 합계) — 비율 계산용 */
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

    public static REFProductNameAlias createCandidate(
            String originalName, String aliasName,
            long occurrenceCount, long totalSelectionCount) {
        REFProductNameAlias alias = new REFProductNameAlias();
        alias.originalName = originalName;
        alias.aliasName = aliasName;
        alias.status = AliasStatus.CANDIDATE;
        alias.occurrenceCount = occurrenceCount;
        alias.totalSelectionCount = totalSelectionCount;
        LocalDateTime now = LocalDateTime.now();
        alias.timeMetaData = new REFEntityTimeMetaData(now, now);
        return alias;
    }

    /* ──────────────────── BUSINESS LOGIC ──────────────────── */

    /**
     * CANDIDATE → CONFIRMED 전환.
     * 3중 게이트를 통과한 경우에만 호출됩니다.
     */
    public void confirm() {
        if (this.status != AliasStatus.CANDIDATE) {
            throw new IllegalStateException(
                    "CANDIDATE 상태에서만 확정 가능. 현재: " + this.status);
        }
        this.status = AliasStatus.CONFIRMED;
    }

    /**
     * CONFIRMED → CANDIDATE 전환 (재심사).
     * 경쟁 후보가 재부상하여 3중 게이트 조건이 더 이상 충족되지 않을 때 호출합니다.
     * 재심사 중에는 파싱 핸들러에서 alias 교체가 중단됩니다.
     * (REFAliasBootstrapInitializer가 CONFIRMED만 로드하므로 Redis에서도 자동 제거됩니다.)
     */
    public void reopen() {
        if (this.status != AliasStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "CONFIRMED 상태에서만 재심사 가능. 현재: " + this.status);
        }
        this.status = AliasStatus.CANDIDATE;
    }

    /** 횟수 갱신 — 새로운 선택이 누적될 때마다 호출됩니다. */
    public void updateCounts(long occurrenceCount, long totalSelectionCount) {
        this.occurrenceCount = occurrenceCount;
        this.totalSelectionCount = totalSelectionCount;
    }

    /* ──────────────────── QUERY ──────────────────── */

    public double aliasRatio() {
        return totalSelectionCount == 0 ? 0.0
                : (double) occurrenceCount / totalSelectionCount;
    }

    public boolean isConfirmed() {
        return this.status == AliasStatus.CONFIRMED;
    }

    public boolean isCandidate() {
        return this.status == AliasStatus.CANDIDATE;
    }

    /* ──────────────────── ENUM ──────────────────── */

    public enum AliasStatus {
        CANDIDATE,   // 임계값 미달 또는 재심사 중
        CONFIRMED    // 3중 게이트 통과, 파싱 파이프라인 적용 중
    }
}