package com.shike.ordering.algorithm;

import java.util.List;
import java.util.Set;

/**
 * 推荐算法看到的菜品。刻意做成一个独立的只读值对象，而不是直接吃数据库实体 ——
 * 这样算法包不依赖 MyBatis、不依赖 Spring，单元测试里 new 出来就能跑。
 *
 * @param id             菜品 id
 * @param name           菜名
 * @param categoryId     分类 id
 * @param price          单价（元）
 * @param spice          辣度 0 不辣 / 1 微辣 / 2 中辣 / 3 特辣
 * @param tags           标记，如「招牌」「素食」「冰饮」「现做」
 * @param monthlySold    近 30 天销量，热度准则的输入
 * @param rating         评分（满分 5）
 * @param prepMinutes    出餐时长（分钟），等待准则的输入
 * @param ingredients    主要食材，忌口过滤用
 *
 * <p>{@code price} 是 {@code double} 而不是 {@code int}：数据库里单价是
 * {@code DECIMAL(8,2)}，如果这里收 int，映射时就得做一次取整，
 * 而"悄悄把 42.50 变成 42"这种错误在推荐结果里几乎看不出来。
 * 直接让类型对得上，就不存在这个环节。TOPSIS 内部本来也都是 double 运算。
 */
public record DishFeature(
        Long id,
        String name,
        String categoryId,
        double price,
        int spice,
        Set<String> tags,
        int monthlySold,
        double rating,
        int prepMinutes,
        List<String> ingredients
) {

    /** 「招牌」是个标签不是分类，算法里几处都要判断，抽出来免得各写各的 */
    public static final String TAG_SIGNATURE = "招牌";
    public static final String TAG_VEGETARIAN = "素食";
    public static final String TAG_ICED = "冰饮";

    /** 虚拟分类 id：招牌推荐。data.js / 数据库里都没有 cat='signature' 的菜 */
    public static final String CAT_SIGNATURE = "signature";

    public DishFeature {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
