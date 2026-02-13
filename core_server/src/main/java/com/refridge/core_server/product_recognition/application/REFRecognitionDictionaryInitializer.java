package com.refridge.core_server.product_recognition.application;


import com.refridge.core_server.product_recognition.domain.REFRecognitionDictionaryRepository;
import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryName;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class REFRecognitionDictionaryInitializer implements ApplicationRunner {

    private final REFRecognitionDictionaryRepository dictionaryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        initializeIfAbsent(
                REFRecognitionDictionaryType.EXCLUSION,
                "비식재료 필터 사전"
        );
        initializeIfAbsent(
                REFRecognitionDictionaryType.GROCERY_ITEM,
                "식재료 매칭 사전"
        );
    }

    private void initializeIfAbsent(REFRecognitionDictionaryType type, String name) {
        boolean exists = dictionaryRepository.existsByDictTypeAndDictName(type, REFRecognitionDictionaryName.of(name));
        if (!exists) {
            REFRecognitionDictionary dictionary = switch (type) {
                case EXCLUSION -> REFRecognitionDictionary.createExclusionDictionary(name);
                case GROCERY_ITEM -> REFRecognitionDictionary.createIngredientDictionary(name);
            };
            dictionaryRepository.save(dictionary);
        }
    }
}
