package com.refridge.core_server.product_recognition.domain.event;

import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionProcessingPath;

import java.util.UUID;

/**
 * Recognition 파이프라인이 완료되었을 때 발행하는 이벤트.
 * 구독 후보:
 *  - 통계/분석 컨텍스트: 어느 경로로 얼마나 매칭됐는지 집계
 *  - 피드백 컨텍스트: 완료된 인식에 대해 피드백 수집 가능 상태 전환
 *  - 알림 컨텍스트: 특정 조건의 완료 시 사용자 알림 (미래 확장)
 */
public record REFRecognitionCompletedEvent (
        UUID recognitionId,
        String requesterId,
        REFRecognitionProcessingPath processingPath,
        boolean isRejected   // true면 비식재료 반려, false면 정상 완료 또는 NO_MATCH
) {
}
