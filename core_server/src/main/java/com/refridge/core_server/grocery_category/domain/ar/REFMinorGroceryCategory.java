package com.refridge.core_server.grocery_category.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryPathSeparator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Optional;

@Entity
@Table(name = "ref_minor_grocery_category")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFMinorGroceryCategory {

    @Id
    @Getter
    @Column(name = "minor_category_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private REFGroceryCategoryName categoryName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_category_id", nullable = false)
    private REFMajorGroceryCategory majorCategory;

    @Embedded
    private REFEntityTimeMetaData timeMetaData;

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

    /* INTERNAL CONSTRUCTOR */
    private REFMinorGroceryCategory(REFGroceryCategoryName categoryName, REFMajorGroceryCategory majorCategory) {
        this.categoryName = categoryName;
        this.majorCategory = majorCategory;
    }

    /* CREATION FACTORY METHOD */
    public static REFMinorGroceryCategory create(String categoryName, REFMajorGroceryCategory majorCategory) {
        return Optional.ofNullable(majorCategory)
                .map(major -> new REFMinorGroceryCategory(
                        REFGroceryCategoryName.of(categoryName),
                        major
                ))
                .filter(minorCategory -> REFMinorGroceryCategory.isValidCategoryNameCondition(minorCategory.getMinorCategoryNameText()))
                .orElseThrow(() -> new IllegalArgumentException("대분류는 필수입니다."));
    }

    /* BUSINESS LOGIC : 중분류의 이름을 변경할 수 있다. */
    public REFMinorGroceryCategory changeCategoryName(String newCategoryName) {
        return Optional.ofNullable(newCategoryName)
                .map(String::trim)
                .filter(REFMinorGroceryCategory::isValidCategoryNameCondition)
                .map(REFGroceryCategoryName::of)
                .map(this::getMinorGroceryCategoryWithModifiedCategoryName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않은 카테고리 이름입니다."
                ));
    }

    /* INTERNAL METHOD : 카테고리명을 변경한 중분류 식재료 카테고리를 반환한다. */
    private REFMinorGroceryCategory getMinorGroceryCategoryWithModifiedCategoryName(REFGroceryCategoryName categoryName) {
        this.categoryName = categoryName;
        return this;
    }

    /* INTERNAL METHOD : 카테고리명 생성 조건을 체크한다. */
    public static boolean isValidCategoryNameCondition(String categoryName) {
        return !categoryName.isEmpty() && categoryName.length() <= 20;
    }

    /* BUSINESS LOGIC : 대분류를 변경할 수 있다. */
    public void changeMajorCategory(REFMajorGroceryCategory otherMajorCategory) {
        Optional.ofNullable(otherMajorCategory)
                .ifPresentOrElse(
                        major -> this.majorCategory = major,
                        () -> {
                            throw new IllegalArgumentException("대분류는 필수입니다.");
                        }
                );
    }

    /* BUSINESS LOGIC : 대분류를 포함해서 문자열 포매팅 된 카테고리 전문을 획득할 수 있다. */
    public String formatFullCategoryPath() {
        return Optional.ofNullable(this.majorCategory)
                .map(major -> this.formatFullCategoryPathWithCategoryNames(
                        major.getMajorCategoryNameText(), this.getMinorCategoryNameText()))
                .orElse(this.categoryName.getValue());
    }

    /* INTERNAL METHOD : " > " 문자열을 기준으로 앞 뒤에 대분류와 중분류를 삽입할 수 있도록 포매팅 한다. */
    private String formatFullCategoryPathWithCategoryNames(String majorCategoryName, String minorCategoryName) {
        return majorCategoryName + REFGroceryCategoryPathSeparator.SEPARATOR + minorCategoryName;
    }

    /* INTERNAL METHOD : 현재 중분류의 카테고리 텍스트 획득 */
    protected String getMinorCategoryNameText() {
        return this.categoryName.getValue();
    }

    /* BUSINESS LOGIC : 대분류 카테고리 ID가 이 중분류 카테고리와 알맞은 대분류 카테고리인지 확인한다. */
    public boolean checkOwnMajorCategory(Long majorCategoryId) {
        return Optional.ofNullable(this.majorCategory)
                .map(major -> major.getId().equals(majorCategoryId))
                .orElse(false);
    }

    /* PACKAGE METHOD */
    void detachFromMajor() {
        this.majorCategory = null;
    }
}
