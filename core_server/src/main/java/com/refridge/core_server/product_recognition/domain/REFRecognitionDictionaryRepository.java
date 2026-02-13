package com.refridge.core_server.product_recognition.domain;


import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryName;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface REFRecognitionDictionaryRepository extends JpaRepository<REFRecognitionDictionary, Long>, REFRecognitionDictionaryRepositoryCustom {

    boolean existsByDictTypeAndDictName(REFRecognitionDictionaryType dictType, REFRecognitionDictionaryName dictName);
}
