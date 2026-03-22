package com.refridge.core_server.recognition_feedback.domain.event;

import com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionFeedbackId;
import com.refridge.core_server.recognition_feedback.domain.vo.REFRecognitionReference;

/**
 * 사용자가 인식 결과를 승인(긍정 피드백)했을 때 발행되는 도메인 이벤트입니다.
 * <p>
 * 구독 후보:
 * <ul>
 *   <li>피드백 집계 서비스: 긍정 카운트 증가 → N회 도달 시 Product 자동 등록</li>
 *   <li>핸들러 품질 메트릭: completedBy 기준 긍정 피드백 비율 집계</li>
 * </ul>
 *
 * @param feedbackId            피드백 AR ID
 * @param recognitionReference  연관된 인식 결과 참조
 * @param snapshot              인식 시점의 결과 스냅샷
 * @param purchasePrice         사용자가 입력한 구매 가격 (nullable)
 */
public record REFPositiveFeedbackEvent(
        REFRecognitionFeedbackId feedbackId,
        REFRecognitionReference recognitionReference,
        REFOriginalRecognitionSnapshot snapshot,
        Long purchasePrice
) {
}