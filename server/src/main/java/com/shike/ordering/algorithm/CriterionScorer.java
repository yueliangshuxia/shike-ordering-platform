package com.shike.ordering.algorithm;

import java.util.Map;
import java.util.Set;

/**
 * 把菜品属性换算成各项准则的原始得分，构成 TOPSIS 决策矩阵的列。
 *
 * <p>五个准则里有三个需要换算（口味、热度、清淡），另外两个（价格、等待）直接用原始值，
 * 归一化留给 TOPSIS 统一做。
 *
 * <p>唯一的对外入口是 {@link #score}，返回的数组下标就是 {@link Criterion#ordinal()}。
 */
public final class CriterionScorer {

    private CriterionScorer() {}

    /**
     * 热度准则里销量归一化的基准值。
     *
     * <p>取当前菜单里销量最高那道菜的量级。菜单一换就要跟着调，
     * 否则热度分会整体偏低或集体顶格，这个准则就失去区分度了。
     *
     * <p>当前菜单（港式茶餐厅）卖得最好的是麻婆豆腐，月销 1673，
     * 所以取 1700。上一版菜单是烧烤店，最高月销 4216，基准值也就跟着是 4300 ——
     * 这也是为什么它必须和菜单一起改，不能定死。
     */
    private static final double SOLD_BENCHMARK = 1700.0;

    /**
     * 忌口的别名表。只做子串匹配，不做同义词网 ——
     * 认真的食材知识库是另一件事，这里够表达意图，而且用户看得懂为什么被压分。
     */
    private static final Map<String, Set<String>> AVOID_ALIASES = Map.of(
            "内脏", Set.of("肝", "肚", "肠", "腰", "胗", "毛肚"),
            "牛羊肉", Set.of("牛", "羊")
    );

    /**
     * 长得像、但明明不是那类食材的名字。命中这些词直接放过，不做子串匹配。
     *
     * <p>这是子串匹配的代价，实测踩到的：勾了「忌口内脏」之后，
     * 「田园时蔬沙拉」因为含 <b>红腰豆</b> 被扣了 30 分，
     * 「菌油拌面」因为含 <b>牛肝菌</b> 也被扣了 —— 一个是豆子、一个是菌菇，
     * 和内脏毫无关系。腰果同理。
     *
     * <p>答辩时这种错误特别扎眼：老师问一句"我忌口内脏，为什么沙拉被降权了"，
     * 答不上来就很难看。所以宁可多维护一张短名单，也不要让用户看到
     * 一个说不通的排序。
     *
     * <p>更彻底的做法是做食材本体标注（红腰豆属于豆类而非内脏），
     * 但那要引入食品知识库，超出本科毕设的范围。这张名单是当前复杂度下
     * 性价比最高的解法，而且它写在这里，别人看得见、加得动。
     */
    private static final Set<String> AVOID_FALSE_FRIENDS = Set.of(
            "红腰豆",   // 豆类，不是猪腰
            "腰果",     // 坚果，不是猪腰
            "牛肝菌"    // 菌菇，不是牛肝
    );

    /** 命中一项忌口扣多少分。扣 30 意味着两项就能把一道菜从 100 压到 40 */
    private static final int AVOID_PENALTY = 30;

    /** 超过辣度上限的惩罚。比忌口更狠，因为「不能吃辣」是硬约束，"不爱吃"是软偏好 */
    private static final int OVER_SPICE_PENALTY = 45;

    /**
     * 算出这道菜在五个准则上的原始值。
     *
     * @return 长度 {@link Criterion#count()} 的数组，下标 = {@link Criterion#ordinal()}
     */
    public static double[] score(DishFeature dish, UserTaste taste) {
        double[] raw = new double[Criterion.count()];
        raw[Criterion.TASTE.ordinal()]   = tasteScore(dish, taste);
        raw[Criterion.PRICE.ordinal()]   = dish.price();
        raw[Criterion.HOT.ordinal()]     = hotScore(dish);
        raw[Criterion.WAIT.ordinal()]    = dish.prepMinutes();
        raw[Criterion.HEALTHY.ordinal()] = healthyScore(dish);
        return raw;
    }

    /**
     * 口味匹配度，归一化到 0–100。
     *
     * <p>这是五项里唯一需要用户画像才能算的，其余四项都是菜品自身属性 ——
     * 也正是这一点让新客第一次打开就能出结果。
     */
    public static double tasteScore(DishFeature dish, UserTaste taste) {
        double score = 40.0;

        // 分类命中用户偏好
        if (taste.likes(dish.categoryId())) {
            score += 34;
        }
        // 招牌菜加权：用户偏好「招牌推荐」这个虚拟分类时生效
        if (taste.likesSignature() && dish.hasTag(DishFeature.TAG_SIGNATURE)) {
            score += 10;
        }

        // 辣度贴合：和用户习惯的辣度越接近加分越多
        int preferredSpice = taste.avoidSpice() ? 0 : 1;
        score += (3 - Math.abs(dish.spice() - preferredSpice)) * 5.0;

        // 超过辣度上限直接压下去
        if (dish.spice() > taste.maxSpice()) {
            score -= OVER_SPICE_PENALTY;
        }

        // 命中忌口食材，每项再压一档
        score -= avoidHits(dish, taste.avoidIngredients()) * (double) AVOID_PENALTY;

        return clamp(score);
    }

    /**
     * 热度口碑：销量和评分各占一半。
     *
     * <p><b>销量开平方是关键。</b>直接用量销的话，月销四千串的烧烤会把所有蒸点挤出榜单 ——
     * 量级差太大，一个准则就主导了整个排序。开方压缩了头部优势，
     * 让不同价位、不同类别的菜能在同一条榜单上公平比较。这是论文创新点之二。
     */
    public static double hotScore(DishFeature dish) {
        double soldPart = Math.sqrt(dish.monthlySold()) / Math.sqrt(SOLD_BENCHMARK) * 100.0;
        double ratingPart = dish.rating() / 5.0 * 100.0;
        return Math.min(100.0, soldPart * 0.5 + ratingPart * 0.5);
    }

    /** 清淡健康：不辣加分，素食加分，冰饮减分 */
    public static double healthyScore(DishFeature dish) {
        double score = 100.0 - dish.spice() * 26.0;
        if (dish.hasTag(DishFeature.TAG_VEGETARIAN)) {
            score += 12;
        }
        if (dish.hasTag(DishFeature.TAG_ICED)) {
            score -= 6;
        }
        return clamp(score);
    }

    /** 这道菜命中了用户几项忌口 */
    public static int avoidHits(DishFeature dish, Set<String> avoidIngredients) {
        if (avoidIngredients == null || avoidIngredients.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String avoid : avoidIngredients) {
            for (String ingredient : dish.ingredients()) {
                if (matchesIngredient(ingredient, avoid)) {
                    hits++;
                    break;   // 同一项忌口命中多个食材只算一次
                }
            }
        }
        return hits;
    }

    /**
     * 单个食材是否命中某一项忌口。
     *
     * <p><b>公开出去是有意的</b>：推荐接口生成「含你忌口的食材：××」这句提示时
     * 必须用同一个判断。两边各写一份的话，迟早会漂成
     * 「这道菜被扣了 30 分，但理由里一个字都没提为什么」——
     * 用户看不到解释，只会觉得算法有问题。
     */
    public static boolean matchesIngredient(String ingredient, String avoid) {
        if (ingredient == null || avoid == null) {
            return false;
        }
        if (AVOID_FALSE_FRIENDS.contains(ingredient)) {
            return false;
        }
        Set<String> needles = AVOID_ALIASES.getOrDefault(avoid, Set.of(avoid));
        return needles.stream().anyMatch(ingredient::contains);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }
}
