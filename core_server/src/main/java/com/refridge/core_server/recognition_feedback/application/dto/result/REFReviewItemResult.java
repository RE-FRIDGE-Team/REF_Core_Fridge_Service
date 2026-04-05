package com.refridge.core_server.recognition_feedback.application.dto.result;

import java.time.LocalDateTime;

/**
 * 관리자 검수 큐 목록/상세 조회 응답 DTO입니다.
 * <p>
 * 발생 빈도({@code occurrenceCount}) 내림차순, 생성일 오름차순으로 정렬된 결과를 담습니다.
 *
 * @param reviewId              검수 항목 ID
 * @param reviewTypeCode        검수 유형 코드 (e.g. "EX", "CA", "GI")
 * @param reviewTypeDescription 검수 유형 설명 (e.g. "카테고리 재분류")
 * @param targetValue           검수 대상 값
 * @param contextDetail         부가 컨텍스트 (원본 제품명, 핸들러 등)
 * @param sourceHandlerName     검수 요청을 발생시킨 파이프라인 핸들러 이름 (nullable)
 * @param occurrenceCount       동일 요청 누적 횟수 (높을수록 우선 처리 권장)
 * @param statusCode            현재 상태 코드
 * @param statusKorName         현재 상태 한글명
 * @param adminNote             관리자 메모 (nullable)
 * @param resolvedAt            처리 완료 시점 (PENDING이면 null)
 * @param createdAt             최초 생성 시점
 */
public record REFReviewItemResult(
        Long reviewId,
        String reviewTypeCode,
        String reviewTypeDescription,
        String targetValue,
        String contextDetail,
        String sourceHandlerName,
        int occurrenceCount,
        String statusCode,
        String statusKorName,
        String adminNote,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
}
