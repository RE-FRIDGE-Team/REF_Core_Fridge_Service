package com.refridge.core_server.bootstrap;

import com.refridge.core_server.grocery_item_correction.domain.REFGroceryItemNameCorrection;
import com.refridge.core_server.grocery_item_correction.domain.REFGroceryItemNameCorrectionRepository;
import com.refridge.core_server.recognition_feedback.infra.event.improvement.REFGroceryItemCorrectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 부팅 시 CONFIRMED 상태인 식재료명 교정 기록을 Redis에 로드합니다.
 *
 * <h3>로드 대상</h3>
 * <p>
 * {@code ref_grocery_item_name_correction} 테이블에서 {@code status = CONFIRMED}인 레코드를
 * {@code grocery-item-correction:confirmed} Hash에 {@code originalName → correctedName}으로 등록합니다.
 * </p>
 *
 * <h3>기존 Hash 삭제 후 재적재</h3>
 * <p>
 * 서버 재시작 전 DB에서 CONFIRMED → CANDIDATE로 재오픈된 교정 기록이 있을 수 있습니다.
 * 삭제 없이 forEach로 덮어쓰기만 하면 해당 항목이 Redis에 stale 엔트리로 남아
 * 잘못된 식재료명 교정이 계속 적용되는 문제가 발생합니다.
 * 따라서 {@link REFAliasBootstrapInitializer}와 동일하게 기존 Hash를 전부 삭제한 뒤
 * 재적재하여 DB와의 일관성을 보장합니다.
 * </p>
 *
 * <h3>@Order 선택 이유</h3>
 * <p>
 * Order(3)으로 설정하여 기존 초기화 순서(사전 초기화 등)와 충돌하지 않도록 합니다.
 * alias 부트스트랩 이니셜라이저가 이미 사용 중인 번호와 겹치지 않도록 확인 후 조정하세요.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 8.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class REFGroceryItemCorrectionBootstrapInitializer implements ApplicationRunner {

    private final REFGroceryItemNameCorrectionRepository correctionRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<REFGroceryItemNameCorrection> confirmed = correctionRepository.findAllConfirmed();

        // DB에서 CONFIRMED → CANDIDATE로 재오픈된 교정 기록이 Redis에 잔존하지 않도록
        // 항상 전체 삭제 후 재적재합니다. confirmed가 비어있어도 delete는 호출해야 합니다.
        redisTemplate.delete(REFGroceryItemCorrectionService.CORRECTION_CONFIRMED_KEY);

        if (confirmed.isEmpty()) {
            log.info("[식재료명 교정 초기화] CONFIRMED 교정 기록 없음, Redis Hash 초기화 후 스킵.");
            return;
        }

        confirmed.forEach(correction ->
                redisTemplate.opsForHash().put(
                        REFGroceryItemCorrectionService.CORRECTION_CONFIRMED_KEY,
                        correction.getOriginalName(),
                        correction.getCorrectedName()
                )
        );

        log.info("[식재료명 교정 초기화] Redis 로드 완료. {}건", confirmed.size());
    }
}