package com.refridge.core_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

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

    /**
     * {@code CacheManager} 빈을 생성합니다.<p>
     * {@code @Cacheable}, {@code @CacheEvict}, {@code @CachePut} 어노테이션 기반 캐싱을 지원합니다.<p>
     *
     * <p>Spring Data Redis 4.0의 {@code GenericJacksonJsonRedisSerializer} 빌더를 사용합니다.<p>
     * {@code BasicPolymorphicTypeValidator}로 허용 타입을 {@code java.} 및 프로젝트 패키지로 제한합니다.
     *
     * @return {@code RedisCacheManager}
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        var typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType("java.")
                .allowIfSubType("com.refridge.")
                .build();

        GenericJacksonJsonRedisSerializer valueSerializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(typeValidator)
                        .build();

        // 기본 캐시 설정 (30분)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(valueSerializer))
                .disableCachingNullValues();

        // 인식 파이프라인 결과 캐시 (6시간)
        // Product 데이터가 변경되기 전까지 동일 입력 → 동일 결과 보장
        RedisCacheConfiguration recognitionConfig = defaultConfig
                .entryTtl(Duration.ofHours(6));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("recognition:pipeline-result", recognitionConfig)
                .build();
    }
}
