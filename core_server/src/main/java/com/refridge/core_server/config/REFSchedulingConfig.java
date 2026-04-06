package com.refridge.core_server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring {@code @Scheduled} 스케줄러 활성화 설정 클래스입니다.
 *
 * <h3>역할</h3>
 * <p>
 * 이 클래스 하나로 프로젝트 전체의 {@code @Scheduled} 어노테이션이 동작합니다.
 * 현재 등록된 스케줄러:
 * </p>
 * <ul>
 *   <li>{@code REFReviewItemCleanupScheduler} — 검수 항목 일괄 삭제 (매일 02:00 + 즉시 트리거)</li>
 *   <li>{@code REFBrandDictionaryScheduler} — 브랜드 사전 PENDING → ACTIVE 이동 (매일 03:00)</li>
 * </ul>
 *
 * 메인 클래스에 직접 붙여도 동작하지만, 별도 Config로 분리하면
 * 테스트 환경에서 이 Config만 제외하여 스케줄러를 비활성화하기 쉽습니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 5.
 */
@Configuration
@EnableScheduling
public class REFSchedulingConfig {
    // 별도 빈 정의 없음 — @EnableScheduling 활성화만이 목적
}