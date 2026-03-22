package com.refridge.core_server.recognition_feedback.domain.event;

import com.refridge.core_server.recognition_feedback.domain.vo.*;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionId;

/**
 * 사용자가 인식 결과를 수정(부정 피드백)했을 때 발행되는 도메인 이벤트입니다.
 * <p>
 * 구독 후보:
 * <ul>
 *   <li>브랜드 사전 보강: brandChanged 시 브랜드 사전에 추가 후보 적재</li>
 *   <li>제품명 alias 등록: productNameChanged 시 정제명→수정명 매핑 등록</li>
 *   <li>식재료 매핑 재학습: groceryItemChanged 시 Product↔GroceryItem 매핑 보정</li>
 *   <li>카테고리 재분류: categoryChanged 시 관리자 검수 큐 적재</li>
 *   <li>핸들러 품질 메트릭: completedBy 기준 부정 피드백 비율 집계</li>
 * </ul>
 *
 * @param feedbackId    피드백 AR ID
 * @param recognitionId 연관된 인식 결과 ID
 * @param snapshot      인식 시점의 결과 스냅샷
 * @param correction    사용자가 수정한 데이터
 * @param diff          원본 vs 수정의 차이 요약
 */
public record REFNegativeFeedbackEvent(
        REFRecognitionFeedbackId feedbackId,
        REFRecognitionId recognitionId,
        REFOriginalRecognitionSnapshot snapshot,
        REFUserCorrectionData correction,
        REFCorrectionDiff diff
) {
}