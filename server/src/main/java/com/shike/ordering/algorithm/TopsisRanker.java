package com.shike.ordering.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TOPSIS 逼近理想解排序法：给定权重，把每道菜和「理想菜品」比距离，越近排越前。
 *
 * <p>计算步骤（论文 3.3 / 3.4 节）：
 * <ol>
 *   <li>向量归一化 {@code r_ij = x_ij / √(Σ_i x_ij²)}</li>
 *   <li>加权 {@code v_ij = w_j · r_ij}</li>
 *   <li>定正理想解 V⁺（效益型取列最大、成本型取列最小）与负理想解 V⁻</li>
 *   <li>欧氏距离 {@code D⁺_i = √Σ(v_ij − V⁺_j)²}，D⁻ 同理</li>
 *   <li>贴近度 {@code C_i = D⁻ / (D⁺ + D⁻)}，按 C 降序排</li>
 * </ol>
 *
 * <p><b>V⁺/V⁻ 在全部备选上取，不是只在前 k 名里取。</b>只在前几名里取极值的话，
 * 第 k+1 名的极端值会被忽略，算出来的理想解并不是真的理想解。
 */
public final class TopsisRanker {

    private TopsisRanker() {}

    /**
     * @param matrix  决策矩阵，每行一道菜
     * @param weights 五个准则的权重，下标 = {@link Criterion#ordinal()}，需已归一化（和为 1）
     */
    public static TopsisResult rank(List<DishVector> matrix, double[] weights) {
        int m = matrix.size();
        int n = Criterion.count();
        if (m == 0) {
            return new TopsisResult(List.of(), new double[n], new double[n], weights);
        }
        if (weights.length != n) {
            throw new IllegalArgumentException("权重个数应为 " + n + "，实际 " + weights.length);
        }

        // 1. 向量归一化。分母按列求，某列全为 0 时记 1，避免除零
        double[] columnNorm = new double[n];
        for (int j = 0; j < n; j++) {
            double sumOfSquares = 0.0;
            for (DishVector row : matrix) {
                sumOfSquares += row.raw()[j] * row.raw()[j];
            }
            columnNorm[j] = Math.sqrt(sumOfSquares);
            if (columnNorm[j] == 0.0) {
                columnNorm[j] = 1.0;
            }
        }

        // 2. 加权
        double[][] weighted = new double[m][n];
        for (int i = 0; i < m; i++) {
            double[] raw = matrix.get(i).raw();
            for (int j = 0; j < n; j++) {
                weighted[i][j] = raw[j] / columnNorm[j] * weights[j];
            }
        }

        // 3. 正负理想解 —— 效益型取列最大、成本型取列最小
        double[] ideal = new double[n];
        double[] anti = new double[n];
        Criterion[] criteria = Criterion.values();
        for (int j = 0; j < n; j++) {
            double max = Double.NEGATIVE_INFINITY;
            double min = Double.POSITIVE_INFINITY;
            for (int i = 0; i < m; i++) {
                max = Math.max(max, weighted[i][j]);
                min = Math.min(min, weighted[i][j]);
            }
            ideal[j] = criteria[j].benefit() ? max : min;
            anti[j]  = criteria[j].benefit() ? min : max;
        }

        // 4. 距离 + 5. 贴近度
        List<RankedDish> scored = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            double dPlus = 0.0;
            double dMinus = 0.0;
            for (int j = 0; j < n; j++) {
                dPlus  += Math.pow(weighted[i][j] - ideal[j], 2);
                dMinus += Math.pow(weighted[i][j] - anti[j], 2);
            }
            dPlus = Math.sqrt(dPlus);
            dMinus = Math.sqrt(dMinus);
            double denominator = dPlus + dMinus;
            // 分母为 0 说明这道菜同时等于正负理想解，只可能出现在所有菜属性完全一致时
            double closeness = denominator == 0.0 ? 0.0 : dMinus / denominator;
            scored.add(new RankedDish(matrix.get(i).dish(), matrix.get(i).raw(),
                    dPlus, dMinus, closeness, 0));
        }

        scored.sort(Comparator.comparingDouble(RankedDish::closeness).reversed());

        // 名次单独赋一遍，别在排序过程中乱掉
        List<RankedDish> ranked = new ArrayList<>(m);
        for (int i = 0; i < scored.size(); i++) {
            RankedDish d = scored.get(i);
            ranked.add(new RankedDish(d.dish(), d.raw(), d.dPlus(), d.dMinus(), d.closeness(), i + 1));
        }

        return new TopsisResult(ranked, ideal, anti, weights);
    }
}
