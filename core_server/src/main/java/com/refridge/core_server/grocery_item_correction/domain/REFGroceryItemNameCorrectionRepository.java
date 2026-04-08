package com.refridge.core_server.grocery_item_correction.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 식재료명 교정 기록 저장소입니다.
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 */
public interface REFGroceryItemNameCorrectionRepository
        extends JpaRepository<REFGroceryItemNameCorrection, Long> {

    /** 원본 식재료명으로 교정 기록 조회 (상태 무관) */
    Optional<REFGroceryItemNameCorrection> findByOriginalName(String originalName);

    /** CONFIRMED 상태인 교정 기록 전체 조회 — 부팅 시 Redis 초기 로드용 */
    @Query("SELECT c FROM REFGroceryItemNameCorrection c WHERE c.status = 'CONFIRMED'")
    List<REFGroceryItemNameCorrection> findAllConfirmed();

    /** 원본 식재료명 + CONFIRMED 상태 조회 */
    @Query("SELECT c FROM REFGroceryItemNameCorrection c " +
            "WHERE c.originalName = :originalName AND c.status = 'CONFIRMED'")
    Optional<REFGroceryItemNameCorrection> findConfirmedByOriginalName(
            @Param("originalName") String originalName);
}
