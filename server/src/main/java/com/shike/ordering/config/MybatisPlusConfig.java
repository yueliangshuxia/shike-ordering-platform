package com.shike.ordering.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页。DbType 必须显式指定 —— 不指定时它靠 JDBC 元数据猜，
        // 测试里用 H2 就会猜成 H2 方言，生成的 limit 语法和 MySQL 不一致
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 拦截没有 where 条件的 update / delete。
        // 这是个便宜的保险丝：写错一个 Service 方法就可能清空整张菜品表，
        // 而毕业设计的演示数据重建起来要花不少工夫
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
