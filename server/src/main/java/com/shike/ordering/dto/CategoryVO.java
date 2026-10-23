package com.shike.ordering.dto;

/**
 * 分类。
 *
 * @param virtual 是否是虚拟分类。只有「招牌推荐」是 ——
 *                它不对应任何一道菜的 category，而是靠菜品身上的
 *                {@code 招牌} 标签筛出来的。前端据此决定是走
 *                {@code /api/dishes?tag=招牌} 还是 {@code /api/dishes?category=xxx}
 * @param count   该分类下在售的菜品数（不含售罄）。前端用来把空分类置灰，
 *                免得用户点进去看到一片空白
 */
public record CategoryVO(String code, String name, String icon, boolean virtual, long count) {
}
