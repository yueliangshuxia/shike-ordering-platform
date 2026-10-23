package com.shike.ordering;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 食刻点餐系统后端入口。
 *
 * <p>技术栈：Spring Boot 3.3.5 + Java 21 + MyBatis-Plus + MySQL 8 + JWT + WebSocket。
 *
 * <p><b>Redis 是可选的</b>。本机（以及答辩用的机器）都没装 Redis，
 * 所以缓存默认走 Spring 自带的 {@code ConcurrentMapCacheManager}，
 * 只有显式带上 {@code --spring.profiles.active=redis} 才会切到 Redis。
 * 见 {@link com.shike.ordering.config.CacheConfig}。
 */
@SpringBootApplication
@MapperScan("com.shike.ordering.mapper")
@EnableCaching
@EnableScheduling
public class OrderingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderingApplication.class, args);
    }
}
