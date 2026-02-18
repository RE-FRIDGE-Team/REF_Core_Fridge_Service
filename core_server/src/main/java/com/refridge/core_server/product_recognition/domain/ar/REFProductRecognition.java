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
    }

    /* FACTORY METHOD */
    public static REFProductRecognition create(String inputText, String requesterId){
        return new REFProductRecognition(inputText, requesterId);
    }

    /* BUSINESS LOGIC : 비식재료로 판단 후 인식 반려할 수 있다. */
    public void rejectAsNonFood(){
        this.status = REFProductRecognitionStatus.REJECTED;
        this.processingPath = REFRecognitionProcessingPath.EXCLUSION;
        this.entityTimeMetaData = this.entityTimeMetaData.updateModifiedAt(LocalDateTime.now());
        this.completedAt = LocalDateTime.now();
    }

    /* BUSINESS LOGIC : 식재료 사전 매칭을 통해 결과 도출이 될 수 있다. */
    public void completeWithGroceryItemDictionaryMatch(REFProductRecognitionOutput output) {
        modifySuccessfulOutput(output);
        this.processingPath = REFRecognitionProcessingPath.GROCERY_ITEM_DICT;
        // registerEvent(new RecognitionCompletedEvent(this.id, this.processingPath, this.result));
    }

    /* BUSINESS LOGIC : 제품명 색인을 통해 결과 도출이 될 수 있다. */
    public void completeWithProductIndexMatch(REFProductRecognitionOutput output){
        modifySuccessfulOutput(output);
        this.processingPath = REFRecognitionProcessingPath.PRODUCT_INDEX;
        // registerEvent(new RecognitionCompletedEvent(this.id, this.processingPath, this.result));
    }

    /* BUSINESS LOGIC : 머신러닝 모델을 통해 결과 도출이 될 수 있다. */
    public void completeWithMLPrediction(REFProductRecognitionOutput output){
        modifySuccessfulOutput(output);
        this.processingPath = REFRecognitionProcessingPath.ML_MODEL;
        // TODO : ML 모델은 GroceryItem에 해당 원재료가 있어야만 처리가 되는가 대해 어떻게 할지 고민해봐야 함.
        // TODO : 만약 없다면, GroceryItem도 새로 생성하는 로직이 필요할 수도?
        // registerEvent(new RecognitionCompletedEvent(this.id, this.processingPath, this.result));
    }

    /* BUSINESS LOGIC : 머신러닝 모델까지 갔는데, 식재료가 아니라고 판단한 경우 */
    public void failToMatch(){
        this.status = REFProductRecognitionStatus.NO_MATCH;
        this.entityTimeMetaData = this.entityTimeMetaData.updateModifiedAt(LocalDateTime.now());
        this.completedAt = LocalDateTime.now();
    }

    /* BUSINESS LOGIC : 인식 과정이 모두 완료된 경우에만 피드백을 받을 수 있다. */
    public boolean canReceiveFeedback() {
        return this.status == REFProductRecognitionStatus.COMPLETED;
    }

    /* INTERNAL METHOD : 성공적으로 Output이 도출된 경우 시간 관련 필드를 변경하고 output을 재설정한다. */
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
}
