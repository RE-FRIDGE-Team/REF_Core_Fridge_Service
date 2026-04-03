package com.refridge.core_server.recognition_feedback.infra.brand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 브랜드 사전 PENDING → ACTIVE 이동을 정기적으로 처리하는 스케줄러입니다.
 *
 * <h3>실행 조건</h3>
 * <ul>
 *   <li>매일 새벽 3시: PENDING에 남아있는 브랜드를 일괄 처리</li>
 *   <li>즉시 트리거는 REFBrandImprovementHandler에서 BATCH_SIZE 도달 시 처리</li>
 * </ul>
 *
 * <h3>새벽 3시 선택 이유</h3>
 * 인식 파이프라인 트래픽이 가장 낮은 시간대입니다.
 * Trie 재빌드는 rebuild() 완료 전까지 기존 Trie로 서빙되므로
 * 재빌드 중 서비스 중단은 없지만, 낮은 트래픽 시간에 처리하는 게 안전합니다.
 *
 * <h3>멱등성</h3>
 * PENDING가 비어있으면 flush()가 즉시 리턴하므로 불필요한 Trie 재빌드가 없습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFBrandDictionaryScheduler {

    private final REFBrandDictionaryFlushService brandDictionaryFlushService;

    /**
     * 매일 새벽 3시 PENDING 브랜드를 일괄 처리합니다.
     * PENDING가 비어있으면 아무것도 하지 않습니다.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void flushPendingBrands() {
        long pendingSize = brandDictionaryFlushService.getPendingSize();

        if (pendingSize == 0) {
            log.debug("[브랜드 스케줄러] PENDING 없음, 스킵.");
            return;
        }

        log.info("[브랜드 스케줄러] 정기 flush 시작. pendingSize={}", pendingSize);
        brandDictionaryFlushService.flush();
        log.info("[브랜드 스케줄러] 정기 flush 완료.");
    }
}