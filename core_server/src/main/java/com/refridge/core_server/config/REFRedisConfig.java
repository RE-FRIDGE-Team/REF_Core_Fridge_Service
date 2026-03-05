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
        // BasicPolymorphicTypeValidator: public API로 허용할 타입 패키지를 명시적으로 제한
        // 역직렬화 시 허용되지 않은 타입이 들어오면 예외를 던져 보안 취약점을 방지합니다.
        var typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)          // 모든 Object 허용 (프로젝트 내부 DTO용)
                .allowIfSubType("java.")                // java 표준 라이브러리 허용
                .allowIfSubType("com.refridge.")        // 프로젝트 패키지만 허용
                .build();

        GenericJacksonJsonRedisSerializer valueSerializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(typeValidator)
                        .build();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))           // 기본 TTL 30분
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(valueSerializer))
                .disableCachingNullValues();                // null 캐싱 방지

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
