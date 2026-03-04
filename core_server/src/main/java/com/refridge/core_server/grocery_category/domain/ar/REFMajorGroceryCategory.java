package com.refridge.core_server.grocery_category.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.vo.REFCategoryColorTag;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import com.refridge.core_server.grocery_category.domain.vo.REFMajorCategoryTypeGroup;
import com.refridge.core_server.grocery_category.domain.vo.REFMinorCategoryCreationData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code REFMajorGroceryCategory}는 식료품 대분류 카테고리를 나타내는 JPA 엔티티입니다.<p>
 * 대분류 카테고리는 여러 개의 중분류 카테고리를 포함할 수 있으며, 대분류 카테고리의 이름과 생성/수정 시점 등의 메타데이터를 관리합니다.<p>
 * 대분류 카테고리는 {@code REFCategoryColorTag}를 포함하고 있으며, DB에는 저장하지 않고 자체 로직을 통해 계산합니다.
 */
@Entity
@SuppressWarnings("NullableProblems")
@Table(name = "ref_major_grocery_category")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFMajorGroceryCategory extends AbstractAggregateRoot<REFMajorGroceryCategory> {

    @Id
    @Getter
    @Column(name = "major_category_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private REFGroceryCategoryName categoryName;

    @OneToMany(mappedBy = "majorCategory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<REFMinorGroceryCategory> minorCategories = new ArrayList<>();

    @Embedded
    /* 엔티티 등록 시간, 엔티티 업데이트 시간 */
    private REFEntityTimeMetaData timeMetaData;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_group", nullable = false)
    private REFMajorCategoryTypeGroup typeGroup;

    @Getter
    @Transient
    private transient REFCategoryColorTag categoryColorTag;

    /* JPA 생성 시점 콜백 - timeMetaData 자동 초기화 */
    @PrePersist
    protected void onCreate() {
        if (timeMetaData == null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = new REFEntityTimeMetaData(now, now);
        }
    }

    /* JPA 수정 시점 콜백 - updatedAt 자동 업데이트 */
    @PreUpdate
    protected void onUpdate() {
        if (timeMetaData != null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = timeMetaData.updateModifiedAt(now);
        }
    }

    /**
     * 영속성 저장 후 카테고리 태그 컬러 아이디 기반 생성
     */
    @PostPersist
    protected void onPostPersist() {
        this.categoryColorTag = REFCategoryColorTag.fromMajorCategoryId(this.id);
    }

    /**
     * 영속성 로드 후 카테고리 태그 컬러 아이디 기반 생성
     */
    @PostLoad
    protected void onPostLoad() {
        this.categoryColorTag = REFCategoryColorTag.fromMajorCategoryId(this.id);
    }

    /* INTERNAL CONSTRUCTOR */
    private REFMajorGroceryCategory(REFGroceryCategoryName categoryName, REFMajorCategoryTypeGroup typeGroup) {
        this.categoryName = categoryName;
        this.typeGroup = typeGroup;
        this.minorCategories = new ArrayList<>();
    }

    /* CREATION FACTORY METHOD */
    public static REFMajorGroceryCategory create(String categoryName, String typeGroupName) {
        return Optional.ofNullable(categoryName)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(REFGroceryCategoryName::of)
                .map(name -> new REFMajorGroceryCategory(name, REFMajorCategoryTypeGroup.valueOf(typeGroupName)))
                .orElseThrow(() -> new IllegalArgumentException("카테고리 이름은 필수입니다."));
    }

    /* BUSINESS LOGIC : 새로운 대분류 카테고리를 생성하고 저장할 수 있다. */
    public static REFMajorGroceryCategory createAndSave(
            String categoryName,
            String typeGroupName,
            REFMajorGroceryCategoryRepository repository
    ) {
        return Optional.of(categoryName)
                .map(name -> validateDuplicateNameAndCreateMajorCategory(name, typeGroupName, repository))
                .map(repository::save)
                .orElseThrow(() -> new IllegalStateException("카테고리 생성에 실패했습니다."));
    }

    /* INTERNAL METHOD : 카테고리명 중복 검증 후 새 대분류 카테고리 엔티티 생성 */
    private static REFMajorGroceryCategory validateDuplicateNameAndCreateMajorCategory(
            String newCategoryName, String typeGroupName, REFMajorGroceryCategoryRepository repository) {
        validateDuplicateName(newCategoryName, repository);
        return create(newCategoryName, typeGroupName);
    }

    /* INTERNAL METHOD : 카테고리명 중복 검증 로직 */
    private static void validateDuplicateName(
            String categoryName,
            REFMajorGroceryCategoryRepository repository
    ) {
        Optional.of(categoryName)
                .filter(repository::existsByCategoryName)
                .ifPresent(name -> {
                    throw new IllegalArgumentException(
                            String.format("이미 존재하는 대분류입니다: %s", name)
                    );
                });
    }

    /* BUSINESS LOGIC : 이 대분류의 하위 중분류 카테고리를 추가한다. */
    protected REFMinorGroceryCategory addMinorCategoryViaMajorCategory(String newMinorCategoryName,
                                                                       REFInventoryItemType itemType) {
        return ensurePersisted()
                .flatMap(major -> validateMinorCategoryName(newMinorCategoryName))
                .map(name -> REFMinorGroceryCategory.create(name, this, itemType))
                .map(minor -> {
                    this.minorCategories.add(minor);
                    return minor;
                })
                .orElseThrow(() -> new IllegalStateException("중분류 추가에 실패했습니다."));
    }

    /* BUSINESS LOGIC : 이 대분류의 하위 중분류 카테고리를 추가하고 저장한다. */
    public REFMinorGroceryCategory addMinorCategoryAndSaveViaMajorCategory(
            String minorCategoryName,
            String itemTypeName,
            REFMinorGroceryCategoryRepository repository
    ) {
        return Optional.of(minorCategoryName)
                .map(name -> addMinorCategoryViaMajorCategory(name, REFInventoryItemType.valueOf(itemTypeName)))
                .map(repository::save)
                .orElseThrow(() -> new IllegalStateException("중분류 저장에 실패했습니다."));
    }

    /* BUSINESS LOGIC : 이 대분류의 하위 중분류 카테고리 여러 개를 추가한다.*/
    protected List<REFMinorGroceryCategory> addMinorCategoriesViaMajorCategory(
            List<REFMinorCategoryCreationData> minorCategoryDataList) {
        return Optional.ofNullable(minorCategoryDataList)
                .filter(list -> !list.isEmpty())
                .stream()
                .flatMap(Collection::stream)
                .map(data -> addMinorCategoryViaMajorCategory(data.name(), data.itemType()))
                .collect(Collectors.toList());
    }

    /* BUSINESS LOGIC : 이 대분류의 하위 중분류 카테고리 여러 개를 추가하고 저장한다.*/
    public List<REFMinorGroceryCategory> addMinorCategoriesAndSaveViaMajorCategory(
            List<REFMinorCategoryCreationData> minorCategoryDataList,
            REFMinorGroceryCategoryRepository repository
    ) {
        return Optional.of(minorCategoryDataList)
                .map(this::addMinorCategoriesViaMajorCategory)
                .map(repository::saveAll)
                .orElse(Collections.emptyList());
    }

    /* INTERNAL METHOD : 영속 보장 체크 */
    private Optional<REFMajorGroceryCategory> ensurePersisted() {
        return Optional.ofNullable(this.id)
                .map(id -> this)
                .or(() -> {
                    throw new IllegalStateException("대분류가 저장되지 않았습니다.");
                });
    }

    /* INTERNAL METHOD : 중분류 카테고리 이름 검증 메소드 */
    private Optional<String> validateMinorCategoryName(String minorCategoryName) {
        return Optional.ofNullable(minorCategoryName)
                .map(String::trim)
                .filter(REFMinorGroceryCategory::isValidCategoryNameCondition)
                .filter(name -> !hasMinorCategoryWithName(name))
                .or(() -> {
                    throw new IllegalArgumentException(
                            findMinorCategoryByName(minorCategoryName)
                                    .map(m -> String.format("이미 존재하는 중분류입니다: %s", minorCategoryName))
                                    .orElse("유효하지 않은 중분류 이름입니다.")
                    );
                });
    }

    /* BUSINESS LOGIC : 이미 대분류 하위에 해당 이름을 가진 중분류 카테고리가 있는지 확인한다. */
    public boolean hasMinorCategoryWithName(String minorCategoryName) {
        return findMinorCategoryByName(minorCategoryName).isPresent();
    }

    /* BUSINESS LOGIC : 대분류 하위 중분류들 중 이름을 통해 중분류 하나를 선택할 수 있다. */
    public Optional<REFMinorGroceryCategory> findMinorCategoryByName(String minorCategoryName) {
        return this.minorCategories.stream()
                .filter(minor -> minor.getMinorCategoryNameText().equals(minorCategoryName))
                .findFirst();
    }

    /* BUSINESS LOGIC : 특정 중분류를 제거할 수 있다.*/
    protected REFMajorGroceryCategory removeMinorCategory(REFMinorGroceryCategory minorCategory) {
        return Optional.ofNullable(minorCategory)
                .filter(this.minorCategories::contains)
                .map(this::removeMinorCategoryAndBreakRelationWithMajorCategory)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 중분류는 이 대분류에 속하지 않습니다."
                ));
    }

    /* INTERNAL METHOD : 두 연관관계를 모두 끊어주는 컨비니언스 메서드 */
    private REFMajorGroceryCategory removeMinorCategoryAndBreakRelationWithMajorCategory(REFMinorGroceryCategory minorCategory) {
        this.minorCategories.remove(minorCategory);
        minorCategory.detachFromMajor();
        return this;
    }

    /* BUSINESS LOGIC : 특정 중분류를 제거 및 삭제할 수 있다.*/
    public REFMajorGroceryCategory removeMinorCategoryAndDelete(
            REFMinorGroceryCategory minorCategory,
            REFMinorGroceryCategoryRepository repository
    ) {
        return Optional.of(minorCategory)
                .map(this::removeMinorCategory)
                .map(major -> {
                    repository.delete(minorCategory);
                    return major;
                })
                .orElseThrow(() -> new IllegalStateException("중분류 삭제에 실패했습니다."));
    }

    /* BUSINESS LOGIC : 이름으로 중분류를 제거할 수 있다.*/
    protected REFMajorGroceryCategory removeMinorCategoryByName(String minorCategoryName) {
        return findMinorCategoryByName(minorCategoryName)
                .map(this::removeMinorCategory)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("'%s' 중분류를 찾을 수 없습니다.", minorCategoryName)
                ));
    }

    /* BUSINESS LOGIC : 이름으로 중분류를 제거 및 삭제한다. */
    public REFMajorGroceryCategory removeMinorCategoryByNameAndDelete(
            String minorCategoryName,
            REFMinorGroceryCategoryRepository repository
    ) {
        return findMinorCategoryByName(minorCategoryName)
                .map(minor -> removeMinorCategoryAndDelete(minor, repository))
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("'%s' 중분류를 찾을 수 없습니다.", minorCategoryName)
                ));
    }

    /* INTERNAL METHOD : 현재 대분류의 카테고리 텍스트 획득 */
    protected String getMajorCategoryNameText() {
        return this.categoryName.getValue();
    }
}