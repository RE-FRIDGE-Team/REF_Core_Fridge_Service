package com.refridge.core_server.product_recognition.domain.ar;


import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.product_recognition.domain.vo.*;
import com.refridge.core_server.product_recognition.infra.REFProductRecognitionStatusConverter;
import com.refridge.core_server.product_recognition.infra.REFRecognitionProcessingPathConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

@Entity
@SuppressWarnings("NullableProblems")
@Table(name = "ref_product_recognition")
@Builder(access = AccessLevel.PROTECTED)
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

    @Column(name = "processing_path", nullable = false)
    @Convert(converter = REFRecognitionProcessingPathConverter.class)
    private REFRecognitionProcessingPath processingPath;

    @Embedded
    private REFProductRecognitionOutput recognitionOutput;
}
