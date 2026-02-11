package com.refridge.core_server.product_recognition.domain.ar;


import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.product_recognition.domain.vo.*;
import com.refridge.core_server.product_recognition.infra.REFProductRecognitionStatusConverter;
import com.refridge.core_server.product_recognition.infra.REFRecognitionProcessingPathConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

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

    @Column(name = "processing_path")
    @Convert(converter = REFRecognitionProcessingPathConverter.class)
    private REFRecognitionProcessingPath processingPath;

    @Embedded
    private REFProductRecognitionOutput recognitionOutput;

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
        this.recognitionOutput = output;
        this.status = REFProductRecognitionStatus.COMPLETED;
        this.processingPath = REFRecognitionProcessingPath.INGREDIENT_DICT;
        this.entityTimeMetaData = this.entityTimeMetaData.updateModifiedAt(LocalDateTime.now());
        this.completedAt = LocalDateTime.now();
        // registerEvent(new RecognitionCompletedEvent(this.id, this.processingPath, this.result));
    }
}
