package com.refridge.core_server.grocery_category.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.grocery_category.domain.vo.REFGroceryCategoryName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

}
