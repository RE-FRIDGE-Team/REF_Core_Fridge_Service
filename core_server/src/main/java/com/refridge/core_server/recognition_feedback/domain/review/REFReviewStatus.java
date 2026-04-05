package com.refridge.core_server.recognition_feedback.domain.review;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 관리자 검수 항목의 상태를 나타냅니다.
 *
 * <h3>상태 전이</h3>
 * <pre>
 *   PENDING ──────────────────────────▶ APPROVED          (일반 승인)
 *          ├────────────────────────── ▶ REJECTED          (반려)
 *          └─▶ ML_TRAINING_PENDING ──▶ APPROVED           (ML 학습 데이터 내보내기 완료 후)
 * </pre>
 *
 * <h3>ML_TRAINING_PENDING 전환 조건</h3>
 * <p>
 * {@link REFReviewType#NEW_GROCERY_ITEM} 유형의 검수 항목 중
 * {@code sourceHandlerName = "MLPrediction"}인 경우,
 * 관리자가 승인하면 즉시 APPROVED가 아닌 ML_TRAINING_PENDING으로 전환됩니다.
 * Spring Batch 잡({@code REFMLTrainingExportJob})이 CSV 내보내기를 완료하면 최종 APPROVED로 전환됩니다.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum REFReviewStatus {

    /** 관리자 검수 대기 */
    PENDING("P", "검수 대기"),

    /** 관리자가 승인하여 반영 완료 */
    APPROVED("A", "승인 반영됨"),

    /** 관리자가 거부 (반영하지 않음) */
    REJECTED("R", "거부됨"),

    /**
     * ML 모델 학습 데이터로 수집이 확정된 항목.
     * <p>
     * {@code MLPrediction} 핸들러에서 완료된 인식 결과 중
     * 식재료명 수정이 발생한 경우, 관리자 승인 시 이 상태로 전환됩니다.
     * Spring Batch 잡({@code REFMLTrainingExportJob})이 주기적으로 수집하여
     * CSV로 내보내고 최종 APPROVED로 전환합니다.
     * </p>
     */
    ML_TRAINING_PENDING("MT", "ML 학습 대기");

    private final String dbCode;
    private final String korCode;

    public static REFReviewStatus fromDbCode(String dbCode) {
        return Arrays.stream(REFReviewStatus.values())
                .filter(s -> s.dbCode.equals(dbCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않은 검수 상태 코드입니다: " + dbCode));
    }

    public boolean isPending() {
        return this == PENDING;
    }

    /** 관리자 처리가 완료된 상태 (Spring Batch 일괄 삭제 대상 판단에 사용) */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    /** ML 학습 데이터 수집 대기 중인 상태 */
    public boolean isMlTrainingPending() {
        return this == ML_TRAINING_PENDING;
    }
}
