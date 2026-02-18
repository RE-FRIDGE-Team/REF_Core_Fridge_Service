package com.refridge.core_server.product_recognition.application;

import com.refridge.core_server.product_recognition.application.dto.command.REFRecognitionRequestCommand;
import com.refridge.core_server.product_recognition.application.dto.result.REFRecognitionResultResponse;
import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.event.REFRecognitionCompletedEvent;
import com.refridge.core_server.product_recognition.domain.pipeline.REFRecognitionContext;
import com.refridge.core_server.product_recognition.domain.REFProductRecognitionRepository;
import com.refridge.core_server.product_recognition.domain.service.REFRecognitionPipeline;
import com.refridge.core_server.product_recognition.infra.pipeline.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class REFProductRecognitionAppService {

    private final REFProductRecognitionRepository recognitionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /* 핸들러 주입 (순서가 곧 파이프라인 순서) */
    private final REFExclusionFilterHandler exclusionFilterHandler;
    private final REFProductNameParsingHandler productNameParsingHandler;
    private final REFGroceryItemDictMatchHandler groceryItemDictMatchHandler;
    private final REFProductIndexSearchHandler productIndexSearchHandler;
    private final REFMLPredictionHandler mlPredictionHandler;

    @Transactional
    public REFRecognitionResultResponse recognize(REFRecognitionRequestCommand command) {
        // 1. AR 생성 및 저장
        REFProductRecognition recognition = REFProductRecognition.create(
                command.inputText(),
                command.requesterId()
        );
        recognitionRepository.save(recognition);

        // 2. 파이프라인 조립 (순서 명시적으로 보임)
        REFRecognitionPipeline pipeline = new REFRecognitionPipeline(List.of(
                exclusionFilterHandler,          // 1단계: 비식재료 필터
                productNameParsingHandler,        // 2단계: 파싱 (브랜드/수량/용량 추출)
                groceryItemDictMatchHandler,      // 3단계: 식재료 사전 매칭
                productIndexSearchHandler,        // 4단계: 제품 색인 검색
                mlPredictionHandler               // 5단계: ML 예측
        ));

        // 3. 파이프라인 실행
        REFRecognitionContext ctx = pipeline.execute(
                new REFRecognitionContext(command.inputText(), recognition)
        );

        // 4. 파이프라인 완료 후 이벤트 발행 (외부 컨텍스트 알림용)
        publishCompletionEvent(ctx);

        // 5. 결과 반환
        return REFRecognitionResultResponse.from(ctx);
    }

    private void publishCompletionEvent(REFRecognitionContext ctx) {
        eventPublisher.publishEvent(new REFRecognitionCompletedEvent(
                ctx.getRecognition().getIdValue(),
                ctx.getRecognition().getRequesterIdValue().toString(),
                ctx.getRecognition().getProcessingPath(),
                ctx.getOutput() == null
        ));
    }
}
