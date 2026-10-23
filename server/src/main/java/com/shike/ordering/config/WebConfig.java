package com.shike.ordering.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置。
 *
 * <p>开发时前端跑在 Vite（5173），后端跑在 8080，属于不同源，必须显式放行。
 *
 * <p>允许的来源写在 {@code shike.cors.allowed-origins} 里而不是写死 ——
 * 答辩时如果 Vite 的 5173 被占用顺延到了 5174，改配置就行，不用改代码重新打包。
 * <b>不要图省事写成 "*"</b>：一旦开了 allowCredentials，
 * 通配来源会被浏览器直接拒绝，反而更麻烦。
 */
@Configuration
public class WebConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${shike.cors.allowed-origins}") String origins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
