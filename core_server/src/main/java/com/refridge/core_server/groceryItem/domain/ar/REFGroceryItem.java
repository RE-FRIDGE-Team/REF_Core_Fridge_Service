package com.refridge.core_server.groceryItem.domain.ar;

import com.refridge.core_server.common.REFEntityTimeMetaData;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.domain.service.REFGroceryItemCategoryValidateAndAdaptService;
import com.refridge.core_server.groceryItem.domain.vo.*;
import com.refridge.core_server.groceryItem.infra.persistence.REFGroceryItemClassificationConverter;
import com.refridge.core_server.groceryItem.infra.persistence.REFGroceryItemStatusConverter;
import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;
import java.util.Optional;

@Entity
@SuppressWarnings("NullableProblems")
@Builder(access = AccessLevel.PROTECTED)
@Table(name = "ref_grocery_item",
        indexes = {
                @Index(name = "idx_grocery_item_category_refs",
                        columnList = "major_category_id, minor_category_id")
        })
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFGroceryItem extends AbstractAggregateRoot<REFGroceryItem> {

    @Id
    @Getter
    @Column(name = "item_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "grocery_seq")
    @SequenceGenerator(
            name = "grocery_seq",
            sequenceName = "ref_grocery_item_seq",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @Embedded
    private REFGroceryItemName groceryItemName;

    @Embedded
    private REFRepresentativeImage representativeImage;

    @Column(name = "item_type", nullable = false)
    @Convert(converter = REFGroceryItemClassificationConverter.class)
    private REFGroceryItemClassification groceryItemClassification;

    @Column(name = "item_status", nullable = false)
    @Convert(converter = REFGroceryItemStatusConverter.class)
    private REFGroceryItemStatus groceryItemStatus;

    @Embedded
    private REFGroceryCategoryReference groceryCategoryReference;

    /**
     * 사용량 환산 메타데이터 (defaultUnit, portion, spoonVolumeMl, pieceWeightGram,
     * allowedUsageTypes, defaultUsageType).
     * fridge_server의 요리(Cook) 기능에서 UsageSpec 환산 시 참조합니다.
     * 모든 필드가 nullable이며, 미설정 시 {@link REFGroceryItemUsageMetadata#empty()}로 초기화됩니다.
     */
    @Embedded
    private REFGroceryItemUsageMetadata usageMetadata;

    @Embedded
    private REFEntityTimeMetaData timeMetaData;

    @PrePersist
    protected void onCreate() {
        if (timeMetaData == null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = new REFEntityTimeMetaData(now, now);
        }
        if (usageMetadata == null) {
            usageMetadata = REFGroceryItemUsageMetadata.empty();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (timeMetaData != null) {
            LocalDateTime now = LocalDateTime.now();
            timeMetaData = timeMetaData.updateModifiedAt(now);
        }
    }

    /* CREATION FACTORY METHOD */
    public static REFGroceryItem create(String groceryItemName,
                                        String representativeImageUrl,
                                        String groceryItemClassification,
                                        Long majorCategoryId, Long minorCategoryId,
                                        REFGroceryItemCategoryValidateAndAdaptService categoryValidator) {

        return Optional.of(minorCategoryId)
                .filter(ignored -> categoryValidator.isValidCategoryIds(majorCategoryId, minorCategoryId))
                .map(validMinor -> REFGroceryItem.builder()
                        .groceryItemName(REFGroceryItemName.of(groceryItemName))
                        .representativeImage(REFRepresentativeImage.of(representativeImageUrl))
                        .groceryItemClassification(REFGroceryItemClassification.fromTypeCode(groceryItemClassification))
                        .groceryItemStatus(REFGroceryItemStatus.ACTIVE)
                        .groceryCategoryReference(REFGroceryCategoryReference.of(majorCategoryId, minorCategoryId))
                        .usageMetadata(REFGroceryItemUsageMetadata.empty())
                        .build())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 카테고리 정보입니다."));
    }

    public static REFGroceryItem createAndSave(String groceryItemName,
                                               String representativeImageUrl,
                                               String groceryItemClassification,
                                               Long majorCategoryId, Long minorCategoryId,
                                               REFGroceryItemRepository repository,
                                               REFGroceryItemCategoryValidateAndAdaptService categoryValidator) {
        return Optional.of(REFGroceryItem.create(
                        groceryItemName, representativeImageUrl,
                        groceryItemClassification, majorCategoryId, minorCategoryId, categoryValidator))
                .map(repository::save)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 카테고리 정보입니다."));
    }

    /* BUSINESS LOGIC : 식재료의 카테고리를 변경할 수 있다. */
    public void changeCategory(Long majorCategoryId, Long minorCategoryId,
                               REFGroceryItemCategoryValidateAndAdaptService categoryValidatorService) {
        if (isActive() && categoryValidatorService.isValidCategoryIds(majorCategoryId, minorCategoryId)) {
            this.groceryCategoryReference = REFGroceryCategoryReference.of(majorCategoryId, minorCategoryId);
        }
    }

    public void changeGroceryItemName(String newGroceryItemName) {
        if (isActive()) {
            this.groceryItemName = REFGroceryItemName.of(newGroceryItemName);
        }
    }

    public REFRepresentativeImage changeRepresentativeImage(String representativeImageUrl) {
        return this.representativeImage = isActive() ?
                REFRepresentativeImage.of(representativeImageUrl) : this.representativeImage;
    }

    /**
     * BUSINESS LOGIC : 사용량 환산 메타데이터를 일괄 업데이트한다.
     * 활성 상태인 경우에만 변경이 적용됩니다.
     */
    public void updateUsageMetadata(REFGroceryItemUsageMetadata newMetadata) {
        if (isActive() && newMetadata != null) {
            this.usageMetadata = newMetadata;
        }
    }

    /**
     * BUSINESS LOGIC : itemType에 매핑된 카테고리 디폴트 프로필을 적용한다.
     *
     * <h3>적용 규칙</h3>
     * <ul>
     *   <li>매핑되는 프로필이 없으면 변경하지 않습니다 (silent skip).</li>
     *   <li>{@code overrideExisting=false}일 때 기존 메타데이터에 값이 있는 필드는 보존됩니다
     *       — 사용자/관리자가 수동 입력한 값을 부트스트랩이 덮어쓰지 않도록 보호합니다.</li>
     *   <li>{@code overrideExisting=true}일 때 모든 필드를 프로필 값으로 강제 갱신합니다.</li>
     * </ul>
     *
     * @param itemType         minor category에서 결정되는 아이템 타입
     * @param overrideExisting 기존 값 보존(false) 또는 강제 덮어쓰기(true)
     * @return 적용 성공 시 true, 매핑 프로필 없거나 비활성 상태면 false
     */
    public boolean applyDefaultProfile(REFInventoryItemType itemType, boolean overrideExisting) {
        if (!isActive()) {
            return false;
        }

        return REFGroceryItemUsageProfile.findByItemType(itemType)
                .map(profile -> {
                    REFGroceryItemUsageMetadata profileMetadata = profile.toMetadata();
                    REFGroceryItemUsageMetadata current = getUsageMetadataOrEmpty();

                    this.usageMetadata = overrideExisting
                            ? profileMetadata
                            : mergePreservingExisting(current, profileMetadata);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 기존 값이 있으면 보존, 없으면 프로필 값으로 채우는 머지 로직.
     * {@code REFGroceryItemUsageMetadata.of()}로 다시 빌드하여 검증을 거칩니다.
     */
    private static REFGroceryItemUsageMetadata mergePreservingExisting(
            REFGroceryItemUsageMetadata current,
            REFGroceryItemUsageMetadata profile) {

        return REFGroceryItemUsageMetadata.of(
                current.getDefaultUnit() != null ? current.getDefaultUnit() : profile.getDefaultUnit(),
                current.getMinPortionAmount() != null ? current.getMinPortionAmount() : profile.getMinPortionAmount(),
                current.getMaxPortionAmount() != null ? current.getMaxPortionAmount() : profile.getMaxPortionAmount(),
                current.getSpoonVolumeMl() != null ? current.getSpoonVolumeMl() : profile.getSpoonVolumeMl(),
                current.getPieceWeightGram() != null ? current.getPieceWeightGram() : profile.getPieceWeightGram(),
                current.hasUsageTypes() ? current.getAllowedUsageTypes() : profile.getAllowedUsageTypes(),
                current.getDefaultUsageType() != null ? current.getDefaultUsageType() : profile.getDefaultUsageType()
        );
    }

    private boolean isActive() {
        return this.groceryItemStatus.equals(REFGroceryItemStatus.ACTIVE);
    }

    public void delete() {
        this.groceryItemStatus = REFGroceryItemStatus.DELETED;
    }

    public void restore() {
        this.groceryItemStatus = REFGroceryItemStatus.ACTIVE;
    }

    public Long getMajorCategoryId() {
        return this.groceryCategoryReference.getMajorCategoryId();
    }

    public Long getMinorCategoryId() {
        return this.groceryCategoryReference.getMinorCategoryId();
    }

    public String getCategoryNameValue() {
        return this.groceryItemName.getValue();
    }

    /**
     * usageMetadata getter — null-safe.
     * JPA가 모든 컬럼이 null인 @Embedded 필드를 null로 hydrate하는 동작에 대비합니다.
     */
    public REFGroceryItemUsageMetadata getUsageMetadataOrEmpty() {
        return Optional.ofNullable(this.usageMetadata)
                .orElseGet(REFGroceryItemUsageMetadata::empty);
    }
}