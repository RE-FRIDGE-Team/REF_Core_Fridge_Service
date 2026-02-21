package com.refridge.core_server.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 엔티티의 생성 및 수정 시점을 관리하는 임베디드 타입입니다.<p>
 * 생성 시점과 수정 시점을 모두 포함하여, 엔티티의 시간 관련 메타데이터를 캡슐화합니다.<p>
 * <pre>
 * {@code
 * @Embedded
 * private REFEntityTimeMetaData entityTimeMetaData;
 *
 * @PrePersist
 * protected void onCreate() {
 *     if (timeMetaData == null) {
 *         LocalDateTime now = LocalDateTime.now();
 *         timeMetaData = new REFEntityTimeMetaData(now, now);
 *     }
 * }
 *
 * @PreUpdate
 * protected void onUpdate() {
 *      if (timeMetaData != null) {
 *          LocalDateTime now = LocalDateTime.now();
 *          timeMetaData = timeMetaData.updateModifiedAt(now);
 *      }
 * }
 * }
 * </pre>
 * 위와 같이 JPA 엔티티에서 {@code @PrePersist}, {@code @PreUpdate}와 같은 라이프사이클 콜백 메서드를 사용해 자동으로 관리합니다.<p>
 * <strong>⚠️ ImplDetail: {@code @EnableJpaAuditing}를 사용하지 않는 이유는, 테스트코드 작성 시점에서 JPA Auditing이 제대로 동작하지 않는 문제를 방지하기 위함입니다.</strong>
 */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class REFEntityTimeMetaData {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public REFEntityTimeMetaData updateModifiedAt(LocalDateTime updatedAt) {
        return new REFEntityTimeMetaData(this.createdAt, updatedAt);
    }
}
