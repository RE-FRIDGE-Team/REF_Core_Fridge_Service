package com.refridge.core_server.grocery_category.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.REFMinorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "ref_major_grocery_category")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFMajorGroceryCategory {

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

    /* INTERNAL CONSTRUCTOR */
    private REFMajorGroceryCategory(REFGroceryCategoryName categoryName) {
        this.categoryName = categoryName;
        this.minorCategories = new ArrayList<>();
    }

    /* CREATION FACTORY METHOD */
    public static REFMajorGroceryCategory create(String categoryName) {
        return Optional.ofNullable(categoryName)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(REFGroceryCategoryName::of)
                .map(REFMajorGroceryCategory::new)
                .orElseThrow(() -> new IllegalArgumentException("카테고리 이름은 필수입니다."));
    }

    /* BUSINESS LOGIC : 새로운 대분류 카테고리를 생성하고 저장할 수 있다. */
    public static REFMajorGroceryCategory createAndSave(
            String categoryName,
            REFMajorGroceryCategoryRepository repository
    ) {
        return Optional.of(categoryName)
                .map(name -> validateDuplicateNameAndCreateMajorCategory(name, repository))
                .map(repository::save)
                .orElseThrow(() -> new IllegalStateException("카테고리 생성에 실패했습니다."));
    }

    /* INTERNAL METHOD : 카테고리명 중복 검증 후 새 대분류 카테고리 엔티티 생성 */
    private static REFMajorGroceryCategory validateDuplicateNameAndCreateMajorCategory(
            String newCategoryName, REFMajorGroceryCategoryRepository repository) {
        validateDuplicateName(newCategoryName, repository);
        return create(newCategoryName);
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
    protected REFMinorGroceryCategory addMinorCategoryViaMajorCategory(String newMinorCategoryName) {
        return ensurePersisted()
                .flatMap(major -> validateMinorCategoryName(newMinorCategoryName))
                .map(name -> REFMinorGroceryCategory.create(name, this))
                .map(minor -> {
                    this.minorCategories.add(minor);
                    return minor;
                })
                .orElseThrow(() -> new IllegalStateException("중분류 추가에 실패했습니다."));
    }

    /* BUSINESS LOGIC : 이 대분류의 하위 중분류 카테고리를 추가하고 저장한다. */
    public REFMinorGroceryCategory addMinorCategoryAndSaveViaMajorCategory(
            String minorCategoryName,
            REFMinorGroceryCategoryRepository repository
    ) {
        return Optional.of(minorCategoryName)
                .map(this::addMinorCategoryViaMajorCategory)
                .map(repository::save)
                .orElseThrow(() -> new IllegalStateException("중분류 저장에 실패했습니다."));
    }

    /* BUSINESS LOGIC : 이 대분류의 하위 중분류 카테고리 여러 개를 추가한다.*/
    protected List<REFMinorGroceryCategory> addMinorCategoriesViaMajorCategory(List<String> minorCategoryNames) {
        return Optional.ofNullable(minorCategoryNames)
                .filter(names -> !names.isEmpty())
                .stream()
                .flatMap(Collection::stream)
                .map(this::addMinorCategoryViaMajorCategory)
                .collect(Collectors.toList());
    }

    /* BUSINESS LOGIC : 이 대분류의 하위 중분류 카테고리 여러 개를 추가하고 저장한다.*/
    public List<REFMinorGroceryCategory> addMinorCategoriesAndSaveViaMajorCategory(
            List<String> minorCategoryNames,
            REFMinorGroceryCategoryRepository repository
    ) {
        return Optional.of(minorCategoryNames)
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

    /* INTERNAL METHOD : 현재 대분류의 카테고리 텍스트 획득 */
    protected String getMajorCategoryNameText() {
        return this.categoryName.getValue();
    }
}
