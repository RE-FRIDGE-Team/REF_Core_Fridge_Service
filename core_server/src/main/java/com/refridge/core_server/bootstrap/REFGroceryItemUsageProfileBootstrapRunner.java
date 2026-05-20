package com.refridge.core_server.bootstrap;

import com.refridge.core_server.groceryItem.application.REFGroceryItemMetadataService;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.groceryItem.infra.persistence.dto.REFGroceryItemBootstrapDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 부팅 시 GroceryItem 전체에 카테고리 디폴트 프로필 + 특수 케이스 오버라이드를
 * 일괄 적용하는 ApplicationRunner입니다.
 *
 * <h3>동작 조건</h3>
 * <ul>
 *   <li>{@code refridge.bootstrap.usage-profile.enabled=true}일 때만 실행</li>
 *   <li>{@code refridge.bootstrap.usage-profile.override-existing=false}(기본) — 기존 값 보존</li>
 *   <li>한 번 적용 완료 후에는 false로 두는 것이 권장</li>
 * </ul>
 *
 * <h3>실행 순서</h3>
 * <p>
 * {@code @Order(1000)} — 카테고리/식재료 초기화 Runner들이 먼저 동작하도록 큰 값.
 * categoryName 매칭이 필요하므로 그 데이터들이 준비된 후 실행되어야 합니다.
 * </p>
 *
 * <h3>커서 페이징</h3>
 * <p>
 * {@code findBootstrapPageAfter}로 minor/major categoryName과 품목명까지 한 번에 가져옵니다.
 * 페이지마다 독립 트랜잭션으로 처리되어 일부 실패가 전체를 롤백하지 않습니다.
 * </p>
 *
 * <h3>TODO (Layer 2/3)</h3>
 * <ul>
 *   <li>LLM 기반 pieceWeightGram·portion 범위 미세조정 배치</li>
 *   <li>사용자 입력 데이터로 자가 보정하는 정기 스케줄러</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 12.
 */
@Slf4j
@Component
@Order(1000)
@RequiredArgsConstructor
public class REFGroceryItemUsageProfileBootstrapRunner implements ApplicationRunner {

    private static final int PAGE_SIZE = 200;

    private final REFGroceryItemRepository groceryItemRepository;
    private final REFGroceryItemMetadataService metadataService;

    @Value("${refridge.bootstrap.usage-profile.enabled:false}")
    private boolean enabled;

    @Value("${refridge.bootstrap.usage-profile.override-existing:false}")
    private boolean overrideExisting;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[UsageProfile 부트스트랩] 비활성화됨 — 스킵 " +
                    "(refridge.bootstrap.usage-profile.enabled=false)");
            return;
        }

        log.info("[UsageProfile 부트스트랩] 시작. overrideExisting={}, pageSize={}",
                overrideExisting, PAGE_SIZE);

        long cursor = 0L;
        int totalProcessed = 0;
        int totalApplied = 0;
        int totalSkipped = 0;
        int totalFailed = 0;

        while (true) {
            List<REFGroceryItemBootstrapDTO> page =
                    groceryItemRepository.findBootstrapPageAfter(cursor, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }

            for (REFGroceryItemBootstrapDTO dto : page) {
                totalProcessed++;
                try {
                    boolean applied = metadataService.applyDefaultProfileFromBootstrap(
                            dto, overrideExisting);
                    if (applied) {
                        totalApplied++;
                    } else {
                        totalSkipped++;
                        log.debug("[UsageProfile 부트스트랩] 프로필 매핑 없음. " +
                                        "id={}, minor='{}', major='{}'",
                                dto.groceryItemId(),
                                dto.minorCategoryName(), dto.majorCategoryName());
                    }
                } catch (Exception e) {
                    totalFailed++;
                    log.warn("[UsageProfile 부트스트랩] 단건 적용 실패. id={}, name='{}', 사유: {}",
                            dto.groceryItemId(), dto.groceryItemName(), e.getMessage());
                }
            }

            cursor = page.get(page.size() - 1).groceryItemId();
            log.info("[UsageProfile 부트스트랩] 진행률 — 처리={}, 적용={}, 스킵={}, 실패={}, 다음커서={}",
                    totalProcessed, totalApplied, totalSkipped, totalFailed, cursor);
        }

        log.info("[UsageProfile 부트스트랩] 완료. 총 처리={}, 적용={}, 스킵={}, 실패={}",
                totalProcessed, totalApplied, totalSkipped, totalFailed);
    }
}