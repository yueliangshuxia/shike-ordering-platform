package com.shike.ordering.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一道菜的完整视图。
 *
 * <p>标签、食材、规格都<b>拼在同一个对象里</b>返回，而不是让前端再发三次请求。
 * 菜单页一屏就有二十几道菜，逐道去取标签会变成几十次往返 ——
 * 这正是后台组装时特意用批量查询避开的问题（见 {@code DishService}）。
 *
 * @param categoryId 分类 <b>code</b>（如 dimsum），不是数据库自增 id。
 *                   前端的分类栏、推荐算法、URL 锚点用的都是 code，
 *                   把自增 id 暴露出去只会让两边多一层对照表。
 * @param soldOut    售罄。由 {@code stock <= 0} 推出，前端不要自己再判一次。
 *                   <b>售罄和上架是两件事</b>：售罄是今天卖完了，
 *                   下架是这道菜不做了。菜单页只显示上架的菜，
 *                   上架但售罄的菜要显示出来并置灰
 * @param enabled    是否上架。只有后台需要关心
 */
public record DishVO(
        Long id,
        String code,
        String name,
        String categoryId,
        String categoryName,
        BigDecimal price,
        String img,
        String brief,
        String detail,
        List<String> tags,
        List<String> ingredients,
        List<SpecVO> specs,
        Integer spice,
        Integer sold,
        BigDecimal rating,
        Integer prepMin,
        Integer stock,
        boolean soldOut,
        Integer enabled,
        String unit,
        String specLabel
) {

    /**
     * 规格。
     *
     * @param priceDelta 相对基准价的加价
     * @param price      加完之后的实际单价。前端直接显示这个，
     *                   不要自己再拿基准价加一遍 —— 演示版就是这么
     *                   把「大份 10 只」的价格算错过的
     */
    public record SpecVO(Long id, String name, BigDecimal priceDelta, BigDecimal price) {
    }
}
