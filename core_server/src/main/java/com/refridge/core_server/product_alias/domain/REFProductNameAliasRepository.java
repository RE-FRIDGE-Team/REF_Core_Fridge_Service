package com.refridge.core_server.product_alias.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface REFProductNameAliasRepository extends JpaRepository<REFProductNameAlias, Long> {

    /** 원본 제품명으로 alias 조회 (상태 무관) */
    Optional<REFProductNameAlias> findByOriginalName(String originalName);

    /** CONFIRMED 상태인 alias 전체 조회 - 부팅 시 Redis 초기 로드용 */
    @Query("SELECT a FROM REFProductNameAlias a WHERE a.status = 'CONFIRMED'")
    List<REFProductNameAlias> findAllConfirmed();

    /** 원본 제품명 + CONFIRMED 상태 조회 */
    @Query("SELECT a FROM REFProductNameAlias a " +
            "WHERE a.originalName = :originalName AND a.status = 'CONFIRMED'")
    Optional<REFProductNameAlias> findConfirmedByOriginalName(@Param("originalName") String originalName);
}