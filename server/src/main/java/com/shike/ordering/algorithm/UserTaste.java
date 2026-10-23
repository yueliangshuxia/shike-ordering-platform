package com.shike.ordering.algorithm;

import java.util.Map;
import java.util.Set;

/**
 * 用户口味画像 —— 「口味匹配」准则的唯一输入。
 *
 * <p>堂食场景下新客占比高，没有历史评分可用，所以这里只取两类信息：
 * <b>显式偏好</b>（用户在个人中心勾选的）和 <b>隐式行为</b>（历史订单里各分类的占比）。
 * 两者在 {@link #fromHistory} 里合成一张分类偏好权重表。
 *
 * @param preferredCategories 各分类的偏好权重，0–1，越大越偏好；空表示没有倾向
 * @param maxSpice            能接受的辣度上限
 * @param avoidSpice          是否完全不吃辣
 * @param avoidIngredients    忌口食材
 */
public record UserTaste(
        Map<String, Double> preferredCategories,
        int maxSpice,
        boolean avoidSpice,
        Set<String> avoidIngredients
) {

    /** 分类偏好权重到这个值时算「命中」 */
    private static final double HIT_THRESHOLD = 0.5;

    public UserTaste {
        preferredCategories = preferredCategories == null ? Map.of() : Map.copyOf(preferredCategories);
        avoidIngredients = avoidIngredients == null ? Set.of() : Set.copyOf(avoidIngredients);
    }

    /** 没有任何偏好信息的新客。推荐算法对新客依然要能出结果，这是本方案相对协同过滤的核心优势 */
    public static UserTaste firstTimeVisitor() {
        return new UserTaste(Map.of(), 3, false, Set.of());
    }

    /** 命中了这个分类的偏好 */
    public boolean likes(String categoryId) {
        return preferredCategories.getOrDefault(categoryId, 0.0) >= HIT_THRESHOLD;
    }

    public boolean likesSignature() {
        return likes(DishFeature.CAT_SIGNATURE);
    }

    /**
     * 由历史订单的分类占比合成分类偏好表。
     *
     * <p>论文 3.5 节写的「显式偏好 + 隐式行为加权」就是这里。
     * 占比直接当权重用：某分类点了 6 成，权重就是 0.6。
     *
     * @param categoryShare 分类 id → 占比（0–1）
     */
    public static UserTaste fromHistory(Map<String, Double> categoryShare, int maxSpice,
                                        boolean avoidSpice, Set<String> avoidIngredients) {
        return new UserTaste(categoryShare, maxSpice, avoidSpice, avoidIngredients);
    }
}
