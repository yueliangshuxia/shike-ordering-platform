package com.shike.ordering.algorithm;

import java.util.List;

/**
 * 个性化菜品推荐的对外入口 —— 两段式的完整流程。
 *
 * <p>先用 AHP 确定准则权重，再用 TOPSIS 对菜品做多准则排序：
 *
 * <pre>
 *   菜品 + 用户画像 ──► 决策矩阵 ──► TOPSIS 排序 ──► 带解释的推荐结果
 *                          ▲
 *        判断矩阵 ──► AHP ──┘（权重）
 * </pre>
 *
 * <p><b>为什么不用协同过滤</b>：堂食场景新客占比高，用户—菜品评分矩阵极稀疏，
 * 冷启动问题重；而且菜品只有几十道，矩阵规模也撑不起协同过滤。
 * 本方案只依赖菜品自身属性和用户显式偏好，新客第一次打开就能出结果，且每一步都能解释。
 *
 * <p>复杂度 O(m·n)：m 道菜、n=5 个准则。几十道菜是微秒级，
 * 所以真实业务里也不急着把结果缓存起来 —— 除非菜品规模上到几千道。
 */
public final class RecommendationEngine {

    private RecommendationEngine() {}

    /**
     * 用默认判断矩阵算出的权重做推荐。
     *
     * @param dishes 候选菜品。<b>调用方需自行过滤掉售罄的菜</b> ——
     *               售罄与否是库存状态不是菜品属性，不该由算法包去关心。
     */
    public static TopsisResult recommend(List<DishFeature> dishes, UserTaste taste) {
        return recommend(dishes, taste, defaultWeights());
    }

    /**
     * 指定权重做推荐。用户在「推荐实验室」里拖滑杆调权重走的就是这个入口。
     *
     * @param weights 长度 {@link Criterion#count()}，下标 = {@link Criterion#ordinal()}
     */
    public static TopsisResult recommend(List<DishFeature> dishes, UserTaste taste, double[] weights) {
        List<DishVector> matrix = dishes.stream()
                .map(d -> DishVector.of(d, taste))
                .toList();
        return TopsisRanker.rank(matrix, weights);
    }

    /** AHP 按默认判断矩阵算出来的权重 */
    public static double[] defaultWeights() {
        return AhpCalculator.calculate().weights();
    }

    /** 五项均分，第 6.2 节对比实验里的「等权重 TOPSIS」基线 */
    public static double[] uniformWeights() {
        int n = Criterion.count();
        double[] w = new double[n];
        java.util.Arrays.fill(w, 1.0 / n);
        return w;
    }
}
