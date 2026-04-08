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

        if (confirmed.isEmpty()) {
            log.info("[식재료명 교정 초기화] CONFIRMED 교정 기록 없음, 스킵.");
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
