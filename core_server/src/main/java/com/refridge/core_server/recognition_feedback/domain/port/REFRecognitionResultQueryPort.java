package com.refridge.core_server.recognition_feedback.domain.port;

import com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * 피드백 BC가 인식 결과 데이터를 조회하기 위한 포트입니다.
 * <p>
 * 피드백 BC는 인식 BC를 직접 참조하지 않고, 이 포트를 통해 필요한 데이터만 가져옵니다.
 * 인프라 레이어에서 인식 BC의 Repository를 사용하는 Adapter가 구현합니다.
 * <p>
 * Lazy Creation 시나리오에서 사용됩니다:
 * AFTER_COMMIT 이벤트가 실패하여 피드백이 아직 없는 상태에서
 * approve/correct 요청이 들어왔을 때, 인식 결과를 조회하여 피드백을 즉시 생성합니다.
 */
public interface REFRecognitionResultQueryPort {

    /**
     * 인식 결과 ID로 스냅샷을 조회합니다.
     *
     * @param recognitionId 인식 결과 UUID
     * @return 인식 결과 스냅샷 (없으면 empty)
     */
    Optional<REFOriginalRecognitionSnapshot> findSnapshotByRecognitionId(UUID recognitionId);

    /**
     * 인식 결과의 요청자 ID를 조회합니다.
     *
     * @param recognitionId 인식 결과 UUID
     * @return 요청자 UUID (없으면 empty)
     */
    Optional<UUID> findRequesterIdByRecognitionId(UUID recognitionId);
}