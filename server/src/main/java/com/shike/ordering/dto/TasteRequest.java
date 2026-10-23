package com.shike.ordering.dto;

import java.util.List;

/**
 * 保存口味画像的入参。
 *
 * <p>四个字段<b>都允许传 null</b>，null 表示"这一项不改"。
 * 用包装类型而不是基本类型正是为了这个 —— {@code int maxSpice = 0}
 * 分不清用户是"选了不辣"还是"没提交这一项"，那就会在只改忌口的时候
 * 把辣度上限悄悄重置成 0。
 */
public record TasteRequest(
        List<String> preferCategories,
        Integer maxSpice,
        Boolean avoidSpice,
        List<String> avoidIngredients
) {
}
