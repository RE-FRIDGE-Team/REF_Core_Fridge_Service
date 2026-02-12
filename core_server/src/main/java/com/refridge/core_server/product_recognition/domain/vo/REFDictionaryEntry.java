package com.refridge.core_server.product_recognition.domain.vo;

import com.refridge.core_server.product_recognition.domain.ar.REFRecognitionDictionary;
import com.refridge.core_server.product_recognition.infra.REFEntrySourceConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dictionary_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFDictionaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dictionary_id", nullable = false)
    private REFRecognitionDictionary dictionary;

    @Column(nullable = false)
    private String text;

    @Column(name = "source", nullable = false)
    @Convert(converter = REFEntrySourceConverter.class)
    private REFEntrySource source;

    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt;

    public static REFDictionaryEntry create(REFRecognitionDictionary dictionary, String text, REFEntrySource source) {
        REFDictionaryEntry entry = new REFDictionaryEntry();
        entry.dictionary = dictionary;
        entry.text = text;
        entry.source = source;
        entry.addedAt = LocalDateTime.now();
        return entry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof REFDictionaryEntry other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
