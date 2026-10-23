package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.algorithm.AhpResult;
import com.shike.ordering.algorithm.AhpCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。
 *
 * <p>不只是回一句 "ok"。答辩现场最怕的是"页面白了但不知道哪一层断了"，
 * 这个接口把数据库、缓存实现、AHP 一致性检验结果一次性摊开 ——
 * 打开浏览器访问一下，就知道是后端没起来、数据库连不上、还是算法算错了。
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final String cacheType;

    public HealthController(JdbcTemplate jdbcTemplate,
                            @Value("${spring.cache.type:simple}") String cacheType) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheType = cacheType;
    }

    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "shike-ordering");
        body.put("status", "UP");

        // 真的执行一条查询，而不是只看连接池有没有对象 ——
        // 池里有连接但数据库挂了的情况是存在的
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("database", Integer.valueOf(1).equals(one) ? "UP" : "DOWN");
        } catch (Exception e) {
            body.put("database", "DOWN: " + e.getClass().getSimpleName());
        }

        body.put("cache", "simple".equals(cacheType) ? "ConcurrentMap（未启用 Redis）" : cacheType);

        AhpResult ahp = AhpCalculator.calculate();
        body.put("ahpLambdaMax", round(ahp.lambdaMax()));
        body.put("ahpCI", round(ahp.consistencyIndex()));
        body.put("ahpCR", round(ahp.consistencyRatio()));
        body.put("ahpPassed", ahp.passed());

        return ApiResponse.ok(body);
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
