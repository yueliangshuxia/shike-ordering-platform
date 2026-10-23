package com.shike.ordering.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果。
 *
 * <p>不直接把 MyBatis-Plus 的 {@code IPage} 丢给前端：那个对象带着
 * {@code orders}、{@code optimizeCountSql}、{@code searchCount} 这些
 * 纯属内部实现的字段，前端一个都用不上，还容易被误当成接口契约的一部分。
 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <T> PageResult<T> of(IPage<T> p) {
        return new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 分页的同时把实体换成 VO，避免调用方拿到 IPage 之后再手动转一遍 */
    public static <E, T> PageResult<T> of(IPage<E> p, Function<E, T> mapper) {
        return new PageResult<>(
                p.getRecords().stream().map(mapper).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }
}
