package com.refridge.core_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/***
 * {@code REFRedisConfig}는 RedisTemplate을 설정하는 클래스입니다.<p>
 * {@code stringRedisTemplate}만 생성하고, 나머지는 캐싱 인프라 서비스를 만들어 {@code ObjectMapper}로 직렬화/역직렬화하여 처리합니다.<p>
 */
@Configuration
@EnableCaching
public class REFRedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    /**
     * RedisConnectionFactory 빈을 생성하여 Redis 서버와의 연결을 설정합니다.<p>
     * Standalone 모드로 Redis 서버에 연결하며, Lettuce 클라이언트를 사용하여 타임아웃 설정을 적용합니다.<p>
     * {@code LettuceClientConfiguration}은 비동기/논블로킹, 기본값, 커넥션 공유의 특징을 갖습니다.
     * @return {@code LettuceConnectionFactory}
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setPassword(RedisPassword.of(password));

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ZERO)
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    /**
     * StringRedisTemplate 빈을 생성합니다.<p>
     * Key/Value 모두 String으로 저장하며, 타입 변환(직렬화/역직렬화)은
     * {@code RedisCacheService}에서 ObjectMapper를 통해 처리합니다.
     * @return {@code StringRedisTemplate}
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
