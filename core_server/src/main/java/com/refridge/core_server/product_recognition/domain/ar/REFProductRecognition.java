package com.refridge.core_server.product_recognition.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.product_recognition.domain.vo.*;
import com.refridge.core_server.product_recognition.infra.converter.REFProductRecognitionStatusConverter;
import com.refridge.core_server.product_recognition.infra.converter.REFRecognitionProcessingPathConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@SuppressWarnings("NullableProblems")
@Table(name = "ref_product_recognition")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFProductRecognition extends AbstractAggregateRoot<REFProductRecognition> {

    @EmbeddedId
    private REFRecognitionId id;

    @Embedded
    private REFClientInputText clientInputText;

    @Embedded
    private REFRequesterId requesterId;

    @Embedded
    private REFEntityTimeMetaData entityTimeMetaData;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "status", nullable = false)
    @Convert(converter = REFProductRecognitionStatusConverter.class)
    private REFProductRecognitionStatus status;

    @Getter
    @Column(name = "processing_path")
    @Convert(converter = REFRecognitionProcessingPathConverter.class)
    private REFRecognitionProcessingPath processingPath;

    @Embedded
    private REFProductRecognitionOutput recognitionOutput;

    @Embedded
    private REFParsedProductResult parsedResult;

    @Embedded
    private REFRejectionDetail rejectionDetail;

    /* JPA 생성 시점 콜백 - createdAt 자동 업데이트 */
    @PrePersist
    protected void onCreate() {
        if (entityTimeMetaData == null) {
            LocalDateTime now = LocalDateTime.now();
            entityTimeMetaData = new REFEntityTimeMetaData(now, now);
        }
    }

    /* JPA 수정 시점 콜백 - updatedAt 자동 업데이트 */
    @PreUpdate
    protected void onUpdate() {
        if (entityTimeMetaData != null) {
            LocalDateTime now = LocalDateTime.now();
            entityTimeMetaData = entityTimeMetaData.updateModifiedAt(now);
        }
    }

    private REFProductRecognition(String input, String requesterId){
        this.id = REFRecognitionId.generate();
        this.clientInputText = REFClientInputText.of(input);
        this.requesterId = REFRequesterId.of(requesterId);
        this.status = REFProductRecognitionStatus.PENDING;
        this.processingPath = REFRecognitionProcessingPath.WAITING;
        this.recognitionOutput = null;
        // ──────────────────────────────────────────────────────
        // [신규 추가] 생성 시점에는 파싱 전이므로 empty
        // ──────────────────────────────────────────────────────
        this.parsedResult = REFParsedProductResult.empty();
        // [신규 추가] 생성 시점에는 반려 전이므로 empty
        this.rejectionDetail = REFRejectionDetail.empty();

        LocalDateTime now = LocalDateTime.now();
        this.entityTimeMetaData = new REFEntityTimeMetaData(now, now);
    }

    /* FACTORY METHOD — 변경 없음 */
    public static REFProductRecognition create(String inputText, String requesterId){
        return new REFProductRecognition(inputText, requesterId);
    }

    // ──────────────────────────────────────────────────────────────
    // [신규 추가] 파싱 결과 저장 메서드
    // 파이프라인에서 ProductNameParsingHandler 실행 후,
    // Context의 파싱 결과를 AR에 옮길 때 사용합니다.
    // ──────────────────────────────────────────────────────────────

    /**
     * 파이프라인의 파싱 결과를 AR에 저장합니다.
     * <p>
     * 호출 시점: 파이프라인 실행 완료 후, Application Service에서
     * Context의 parsedProductName을 AR에 옮길 때.
     *
     * @param parsed 파이프라인 실행 중 생성된 파싱 결과 (null 가능)
     */
    public void applyParsedResult(REFParsedProductInformation parsed) {
        this.parsedResult = REFParsedProductResult.from(parsed);
    }

    public void rejectAsNonFood(String matchedKeyword){
        this.status = REFProductRecognitionStatus.REJECTED;
        this.processingPath = REFRecognitionProcessingPath.EXCLUSION;
        this.rejectionDetail = REFRejectionDetail.of(matchedKeyword);
        this.entityTimeMetaData = this.entityTimeMetaData.updateModifiedAt(LocalDateTime.now());
        this.completedAt = LocalDateTime.now();
    }

    public void completeWithGroceryItemDictionaryMatch(REFProductRecognitionOutput output) {
        modifySuccessfulOutput(output);
        this.processingPath = REFRecognitionProcessingPath.GROCERY_ITEM_DICT;
    }

    public void completeWithProductIndexMatch(REFProductRecognitionOutput output){
        modifySuccessfulOutput(output);
        this.processingPath = REFRecognitionProcessingPath.PRODUCT_INDEX;
    }

    public void completeWithMLPrediction(REFProductRecognitionOutput output){
        modifySuccessfulOutput(output);
        this.processingPath = REFRecognitionProcessingPath.ML_MODEL;
    }

    public void failToMatch(){
        this.status = REFProductRecognitionStatus.NO_MATCH;
        this.entityTimeMetaData = this.entityTimeMetaData.updateModifiedAt(LocalDateTime.now());
        this.completedAt = LocalDateTime.now();
    }

    public boolean canReceiveFeedback() {
        return this.status == REFProductRecognitionStatus.COMPLETED
                || this.status == REFProductRecognitionStatus.REJECTED;
    }

    private void modifySuccessfulOutput(REFProductRecognitionOutput output){
        this.recognitionOutput = output;
        this.status = REFProductRecognitionStatus.COMPLETED;
        this.entityTimeMetaData = this.entityTimeMetaData.updateModifiedAt(LocalDateTime.now());
        this.completedAt = LocalDateTime.now();
    }

    public UUID getIdValue(){
        return this.id.getValue();
    }

    public UUID getRequesterIdValue(){
        return this.requesterId.getId();
    }

    /** 인식 매칭 결과 (groceryItemId, groceryItemName, categoryPath, imageUrl) */
    public REFProductRecognitionOutput getRecognitionOutput() {
        return this.recognitionOutput;
    }

    /** 파싱 결과 (refinedProductName, brandName, quantity, volume, volumeUnit) */
    public REFParsedProductResult getParsedResult() {
        return this.parsedResult;
    }

    // [신규 추가] 반려 사유 (매칭된 비식재료 키워드)
    /** 비식재료 반려 사유 — 반려되지 않았으면 empty */
    public REFRejectionDetail getRejectionDetail() {
        return this.rejectionDetail;
    }
}