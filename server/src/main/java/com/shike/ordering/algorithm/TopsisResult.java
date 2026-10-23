package com.shike.ordering.algorithm;

import java.util.List;

/**
 * TOPSIS 的一次完整计算结果。
 *
 * <p>除了排序，还把正负理想解一并带出来。界面上的「推荐实验室」要把中间过程摊开，
 * 而 V⁺/V⁻ 必须给<b>真正参与过距离计算的这一组</b>（向量归一化 + 加权之后的值）——
 * 另起炉灶拿原始值再求一遍列极值，算出来的数跟 D⁺/D⁻ 对不上，手算复核会出问题。
 *
 * @param ranked 按贴近度降序排好的结果
 * @param ideal  正理想解 V⁺，下标 = {@link Criterion#ordinal()}，已加权归一
 * @param anti   负理想解 V⁻，同上
 * @param weights 本次计算实际使用的权重
 */
public record TopsisResult(List<RankedDish> ranked, double[] ideal, double[] anti, double[] weights) {

    public TopsisResult {
        ranked = List.copyOf(ranked);
        ideal = ideal.clone();
        anti = anti.clone();
        weights = weights.clone();
    }

    @Override public double[] ideal()   { return ideal.clone(); }
    @Override public double[] anti()    { return anti.clone(); }
    @Override public double[] weights() { return weights.clone(); }

    /** 取前 k 名。k 大于总数时返回全部，不抛异常 —— 菜品少的店铺也跑得通 */
    public List<RankedDish> top(int k) {
        return ranked.subList(0, Math.min(k, ranked.size()));
    }
}
