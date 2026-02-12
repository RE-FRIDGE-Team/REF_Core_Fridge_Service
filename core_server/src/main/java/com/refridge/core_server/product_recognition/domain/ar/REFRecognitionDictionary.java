package com.refridge.core_server.product_recognition.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.product_recognition.domain.vo.*;
import com.refridge.core_server.product_recognition.infra.REFRecognitionDictionaryTypeConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@SuppressWarnings("NullableProblems")
@Table(name = "ref_product_recognition")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRecognitionDictionary extends AbstractAggregateRoot<REFRecognitionDictionary> {

    @EmbeddedId
    /* 사전 구분 아이디 */
    private REFRecognitionDictionaryId id;

    @Column(name = "dict_type")
    @Convert(converter = REFRecognitionDictionaryTypeConverter.class)
    /* 사전 타입 : 비식재료 필터 || 식재료 사전 */
    private REFRecognitionDictionaryType dictType;

    @Embedded
    /* 사전 이름 */
    private REFRecognitionDictionaryName dictName;

    @OneToMany(mappedBy = "dictionary", cascade = CascadeType.ALL, orphanRemoval = true)
    /* 사전 요소 */
    private Set<REFDictionaryEntry> entries = new HashSet<>();

    @Embedded
    private REFEntityTimeMetaData entityTimeMetaData;

    /* Redis <-> Aho-corasick 사전과의 버전 관리용 */
    private int version;

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

    /* FOR CREATE METHOD & FACTORY METHOD*/

    public static REFRecognitionDictionary createExclusionDictionary(String name) {
        return create(REFRecognitionDictionaryType.EXCLUSION, name);
    }

    public static REFRecognitionDictionary createIngredientDictionary(String name) {
        return create(REFRecognitionDictionaryType.GROCERY_ITEM, name);
    }

    private static REFRecognitionDictionary create(REFRecognitionDictionaryType dictType, String name) {
        REFRecognitionDictionary dictionary = new REFRecognitionDictionary();
        dictionary.dictType = dictType;
        dictionary.dictName = REFRecognitionDictionaryName.of(name);
        dictionary.version = 0;
        return dictionary;
    }

    /* BUSINESS LOGIC : Entry 관리 메서드 */

    public REFDictionaryEntry addEntry(String text, REFEntrySource source) {
        validateNotDuplicate(text);
        REFDictionaryEntry entry = REFDictionaryEntry.create(this, text, source);
        this.entries.add(entry);
        this.version++;
        return entry;
    }

    public void addEntries(Set<String> texts, REFEntrySource source) {
        texts.forEach(text -> {
            if (!hasEntry(text)) {
                this.entries.add(REFDictionaryEntry.create(this, text, source));
            }
        });
        this.version++;
    }

    public void removeEntry(String text) {
        boolean removed = this.entries.removeIf(e -> e.getText().equals(text));
        if (removed) {
            this.version++;
        }
    }

    /* BUSINESS LOGIC : Entry 조회 메서드 */

    public Set<String> getAllEntryTexts() {
        return this.entries.stream()
                .map(REFDictionaryEntry::getText)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasEntry(String text) {
        return this.entries.stream()
                .anyMatch(e -> e.getText().equals(text));
    }

    /* INTERNAL LOGIC : 검증 메서드 */

    private void validateNotDuplicate(String text) {
        if (hasEntry(text)) {
            throw new IllegalArgumentException("이미 존재하는 사전 항목입니다: " + text);
        }
    }

}
