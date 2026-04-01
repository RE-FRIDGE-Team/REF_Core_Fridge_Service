package com.refridge.core_server.product_alias.domain;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

/**
 * 제품명 alias 집계 루트입니다.<p>
 * 사용자들이 파이프라인 인식 결과를 수정한 이력이 임계값을 충족하면
 * "원본 정제명 -> alias명" 매핑이 CONFIRMED 상태로 저장됩니다.<p>
 * 이후 파싱 파이프라인은 이 테이블을 참조하여 refinedText를 alias로 자동 교체합니다.
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

    @Column(name = "original_name", nullable = false, length = 200)
    private String originalName;

    @Column(name = "alias_name", nullable = false, length = 200)
    private String aliasName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AliasStatus status;

    /** 수정본 선택 누적 횟수 */
    @Column(name = "occurrence_count", nullable = false)
    private long occurrenceCount;

    /** 전체 선택 횟수 (파이프라인 선택 + 수정본 선택) - 비율 계산용 */
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

    public void confirm() {
        if (this.status != AliasStatus.CANDIDATE) {
            throw new IllegalStateException("CANDIDATE 상태에서만 확정 가능. 현재: " + this.status);
        }
        this.status = AliasStatus.CONFIRMED;
    }

    public void updateCounts(long occurrenceCount, long totalSelectionCount) {
        this.occurrenceCount = occurrenceCount;
        this.totalSelectionCount = totalSelectionCount;
    }

    public double aliasRatio() {
        return totalSelectionCount == 0 ? 0.0 : (double) occurrenceCount / totalSelectionCount;
    }

    public boolean isConfirmed() {
        return this.status == AliasStatus.CONFIRMED;
    }

    public enum AliasStatus {
        CANDIDATE,
        CONFIRMED
    }
}