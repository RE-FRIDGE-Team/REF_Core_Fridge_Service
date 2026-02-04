package com.refridge.core_server.grocery_category.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.grocery_category.domain.REFMajorGroceryCategoryRepository;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /* INTERNAL METHOD : 현재 대분류의 카테고리 텍스트 획득 */
    protected String getMajorCategoryNameText() {
        return this.categoryName.getValue();
    }
}
