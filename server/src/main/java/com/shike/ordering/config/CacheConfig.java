package com.shike.ordering.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 缓存实现的选择。
 *
 * <p>本机和答辩用的机器都<b>没装 Redis</b>，所以默认走 Spring 自带的
 * {@code ConcurrentMapCacheManager}（见下面那段注释说明），
 * 只有启动时带上 {@code --spring.profiles.active=redis} 才会切到 Redis。
 *
 * <p>这样安排的意义：菜品列表和推荐结果都带 {@code @Cacheable}，
 * 如果没有降级方案，换一台没装 Redis 的机器启动就会直接报连接失败 ——
 * 答辩现场翻车多半就是这么来的。
 */
@Configuration
public class CacheConfig {

    /**
     * Redis 版。只有显式启用 redis profile 时才生效。
     *
     * <p>注意这里必须给 {@code @Primary}：{@code @EnableCaching} 在同时存在
     * 多个 CacheManager 时会报"expected single matching bean"，
     * 而不是替你挑一个。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                // key 用字符串，值用 JSON —— 默认的 JDK 序列化存进去
                // 用 redis-cli 看是一串二进制乱码，调试时很痛苦
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
        return RedisCacheManager.builder(factory).cacheDefaults(config).build();
    }

    /*
     * 没装 Redis 时的兜底不在这里写：
     * spring.cache.type=simple 会让 Spring Boot 自己装配一个
     * ConcurrentMapCacheManager，行为和 @Cacheable 完全一致，只是
     * 缓存放在 JVM 堆里、重启即失效、多实例之间不共享。
     * 对单机部署的堂食系统来说这三点都不是问题。
     */
}
