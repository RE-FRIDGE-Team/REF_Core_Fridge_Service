package com.refridge.core_server.bootstrap;

import com.refridge.core_server.product_alias.application.REFAliasConfirmationService;
import com.refridge.core_server.product_alias.domain.REFProductNameAlias;
import com.refridge.core_server.product_alias.domain.REFProductNameAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 애플리케이션 부팅 시 DB의 CONFIRMED alias를 Redis Hash에 로드합니다.<p>
 *
 * Redis가 재시작되면 alias:confirmed Hash가 초기화되므로,
 * 이 Initializer가 DB에서 전체 CONFIRMED alias를 다시 채웁니다.<p>
 *
 * Order: 파싱 파이프라인 핸들러보다 먼저 실행되어야 합니다.<p>
 * Dictionary Initializer(Order(1))보다 뒤에 실행해도 무방합니다.<p>
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class REFAliasBootstrapInitializer implements ApplicationRunner {

    private final REFProductNameAliasRepository aliasRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<REFProductNameAlias> confirmedAliases = aliasRepository.findAllConfirmed();

        if (confirmedAliases.isEmpty()) {
            log.info("[Alias 초기화] CONFIRMED alias 없음, 스킵.");
            return;
        }

        // 기존 Hash 삭제 후 전체 재적재 (재시작 시 stale 데이터 방지)
        redisTemplate.delete(REFAliasConfirmationService.ALIAS_CONFIRMED_KEY);

        confirmedAliases.forEach(alias ->
                redisTemplate.opsForHash().put(
                        REFAliasConfirmationService.ALIAS_CONFIRMED_KEY,
                        alias.getOriginalName(),
                        alias.getAliasName()
                )
        );

        log.info("[Alias 초기화] CONFIRMED alias {}건 Redis 로드 완료.", confirmedAliases.size());
    }
}