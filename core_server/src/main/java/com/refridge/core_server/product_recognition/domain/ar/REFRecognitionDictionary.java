package com.refridge.core_server.product_recognition.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.product_recognition.domain.vo.REFDictionaryEntry;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryId;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryName;
import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;
import com.refridge.core_server.product_recognition.infra.REFRecognitionDictionaryTypeConverter;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
public class REFRecognitionDictionary {

    @EmbeddedId
    private REFRecognitionDictionaryId id;

    @Column(name = "dict_type")
    @Convert(converter = REFRecognitionDictionaryTypeConverter.class)
    private REFRecognitionDictionaryType dictType;

    @Embedded
    private REFRecognitionDictionaryName dictName;

    @OneToMany(mappedBy = "dictionary", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<REFDictionaryEntry> entries = new HashSet<>();

    @Embedded
    private REFEntityTimeMetaData entityTimeMetaData;

    private int version;

}
