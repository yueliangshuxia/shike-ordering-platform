package com.shike.ordering.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shike.ordering.entity.DishTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * DishTag 的持久层。
 *
 * <p>继承 BaseMapper 就拿到了增删改查；只有多表联查和统计
 * 才需要在这里手写 SQL —— MyBatis-Plus 的 LambdaQuery 表达不了聚合。
 */
@Mapper
public interface DishTagMapper extends BaseMapper<DishTag> {
}
