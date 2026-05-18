package com.refridge.core_server.bootstrap;

import com.refridge.core_server.groceryItem.application.REFGroceryItemMetadataService;
import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 부팅 시 기존 GroceryItem 전체에 카테고리 디폴트 프로필을 일괄 적용하는 ApplicationRunner입니다.
 *
 * <h3>동작 조건</h3>
 * <ul>
 *   <li>{@code refridge.bootstrap.usage-profile.enabled=true}일 때만 실행</li>
 *   <li>{@code refridge.bootstrap.usage-profile.override-existing=false}가 기본 — 기존 값 보존</li>
 *   <li>한 번 적용 완료 후에는 false로 두는 것이 권장됩니다 (재적용 비용 방지).</li>
 * </ul>
 *
 * <h3>실행 순서</h3>
 * <p>
 * {@code @Order(1000)} — 기존 카테고리/식재료 초기화 ApplicationRunner들이 먼저 동작하도록
 * 충분히 큰 값을 사용합니다. 카테고리/MinorCategory가 채워진 후 실행되어야
 * itemType 매핑이 가능합니다.
 * </p>
 *
 * <h3>커서 페이징</h3>
 * <p>
 * 전체 식재료를 메모리에 올리지 않기 위해 ID 커서 기반 페이징을 사용합니다.
 * 각 페이지는 독립 트랜잭션으로 처리되어 일부 실패가 전체를 롤백하지 않습니다.
 * </p>
 *
 * <h3>TODO (Layer 2/3)</h3>
 * <ul>
 *   <li>LLM 기반 pieceWeightGram·spoonVolumeMl 미세조정 배치 (별도 ApplicationRunner)</li>
 *   <li>사용자 입력 데이터로 자가 보정하는 정기 스케줄러 (Spring Batch + 3중 게이트 패턴)</li>
 * </ul>
 *
 * @author 이승훈
 * @since 2026. 5. 9.
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

        while (true) {
            List<Long> ids = groceryItemRepository.findActiveIdsAfter(cursor, PAGE_SIZE);
            if (ids.isEmpty()) {
                break;
            }

            for (Long id : ids) {
                totalProcessed++;
                try {
                    boolean applied = metadataService.applyDefaultProfile(id, overrideExisting);
                    if (applied) {
                        totalApplied++;
                    } else {
                        totalSkipped++;
                    }
                } catch (Exception e) {
                    totalSkipped++;
                    log.warn("[UsageProfile 부트스트랩] 단건 적용 실패. id={}, 사유: {}",
                            id, e.getMessage());
                }
            }

            cursor = ids.get(ids.size() - 1);
            log.info("[UsageProfile 부트스트랩] 진행률 — 처리={}, 적용={}, 스킵={}, 다음커서={}",
                    totalProcessed, totalApplied, totalSkipped, cursor);
        }

        log.info("[UsageProfile 부트스트랩] 완료. 총 처리={}, 적용={}, 스킵={}",
                totalProcessed, totalApplied, totalSkipped);
    }
}