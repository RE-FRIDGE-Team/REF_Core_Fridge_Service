package com.refridge.core_server.product_recognition.domain;


import com.refridge.core_server.product_recognition.domain.ar.REFProductRecognition;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface REFProductRecognitionRepository extends JpaRepository<REFProductRecognition, REFRecognitionId>, REFProductRecognitionRepositoryCustom {
}
