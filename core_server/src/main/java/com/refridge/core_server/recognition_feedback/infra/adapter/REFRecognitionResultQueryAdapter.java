package com.refridge.core_server.recognition_feedback.infra.adapter;

import com.refridge.core_server.product_recognition.domain.REFProductRecognitionRepository;
import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductResult;
import com.refridge.core_server.product_recognition.domain.vo.REFProductRecognitionOutput;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionId;
import com.refridge.core_server.product_recognition.domain.vo.REFRejectionDetail;
import com.refridge.core_server.recognition_feedback.domain.port.REFRecognitionResultQueryPort;
import com.refridge.core_server.recognition_feedback.domain.vo.REFOriginalRecognitionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 피드백 BC가 인식 결과 데이터를 조회하기 위한 Adapter입니다.
 * <p>
 * 인식 BC의 {@code REFProductRecognition} AR에서 매칭 결과 + 파싱 결과를 꺼내
 * 피드백 BC의 {@code REFOriginalRecognitionSnapshot} VO로 변환합니다.
 * <p>
 * Recognition AR에 {@code REFParsedProductResult}가 추가되었으므로
 * brandName, quantity, volume 등 파싱 결과도 완전히 포함됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFRecognitionResultQueryAdapter implements REFRecognitionResultQueryPort {

    private final REFProductRecognitionRepository recognitionRepository;

    @Override
    public Optional<REFOriginalRecognitionSnapshot> findSnapshotByRecognitionId(UUID recognitionId) {
        return recognitionRepository.findById(REFRecognitionId.of(recognitionId))
                .map(this::toSnapshot);
    }

    @Override
    public Optional<UUID> findRequesterIdByRecognitionId(UUID recognitionId) {
        return recognitionRepository.findById(REFRecognitionId.of(recognitionId))
                .map(REFProductRecognition::getRequesterIdValue);
    }

    /**
     * 인식 AR → 피드백 스냅샷 VO 변환.
     * <p>
     * AR의 두 Embedded VO에서 데이터를 추출하여 하나의 스냅샷으로 조합합니다:
     * <ul>
     *   <li>{@code recognitionOutput} → groceryItemId, groceryItemName, categoryPath, imageUrl</li>
     *   <li>{@code parsedResult} → refinedProductName, brandName, quantity, volume, volumeUnit</li>
     *   <li>{@code processingPath} → completedBy (어느 핸들러에서 완료되었는지)</li>
     * </ul>
     */
    private REFOriginalRecognitionSnapshot toSnapshot(REFProductRecognition recognition) {
        REFProductRecognitionOutput output = recognition.getRecognitionOutput();
        REFParsedProductResult parsed = recognition.getParsedResult();
        REFRejectionDetail rejection = recognition.getRejectionDetail();

        return REFOriginalRecognitionSnapshot.of(
                // 파싱 결과
                parsed != null ? parsed.getRefinedProductName() : null,
                // 매칭 결과
                output != null ? output.getGroceryItemId() : null,
                output != null ? output.getGroceryItemName() : null,
                output != null ? output.getCategoryPath() : null,
                // 파싱 결과 (계속)
                parsed != null ? parsed.getBrandName() : null,
                parsed != null ? parsed.getQuantity() : null,
                parsed != null ? parsed.getVolume() : null,
                parsed != null ? parsed.getVolumeUnit() : null,
                // 매칭 결과 (계속)
                output != null ? output.getImageUrl() : null,
                // 처리 경로
                recognition.getProcessingPath() != null
                        ? recognition.getProcessingPath().name() : null,
                // [신규 추가] 반려 정보
                rejection != null && rejection.hasKeyword(),
                rejection != null ? rejection.getMatchedKeyword() : null
        );
    }
}