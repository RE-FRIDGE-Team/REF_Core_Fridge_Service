package com.refridge.core_server.grocery_category.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

    /* 메서드 내부적 사용 : private 생성자 */
    private REFMinorGroceryCategory(REFGroceryCategoryName categoryName, REFMajorGroceryCategory majorCategory) {
        this.categoryName = categoryName;
        this.majorCategory = majorCategory;
    }

    /* 중분류 생성 팩토리 메서드 */
    static REFMinorGroceryCategory create(String categoryName, REFMajorGroceryCategory majorCategory) {
        return Optional.ofNullable(majorCategory)
                .map(major -> new REFMinorGroceryCategory(
                        REFGroceryCategoryName.of(categoryName),
                        major
                ))
                .orElseThrow(() -> new IllegalArgumentException("대분류는 필수입니다."));
    }

}
