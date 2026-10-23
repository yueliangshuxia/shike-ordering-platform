package com.shike.ordering.algorithm;

/**
 * 决策矩阵的一行：菜品 + 它在五个准则上的原始值。
 *
 * @param dish 菜品
 * @param raw  原始得分，下标 = {@link Criterion#ordinal()}。价格是元、等待是分钟，
 *             其余三项已归一到 0–100。归一化不在这里做，留给 {@link TopsisRanker}
 *             统一处理 —— 保留原始值是为了能在界面上把中间结果摊开给用户看。
 */
public record DishVector(DishFeature dish, double[] raw) {

    public DishVector {
        raw = raw.clone();
    }

    @Override
    public double[] raw() {
        return raw.clone();
    }

    public double raw(Criterion c) {
        return raw[c.ordinal()];
    }

    public static DishVector of(DishFeature dish, UserTaste taste) {
        return new DishVector(dish, CriterionScorer.score(dish, taste));
    }
}
