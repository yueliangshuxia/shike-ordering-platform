package com.shike.ordering.algorithm;

import java.util.Arrays;

/**
 * 层次分析法的一次完整计算结果。
 *
 * <p>论文第 3.3 节的表 3.3「权重与一致性检验结果」直接对应这个对象，
 * 里面的每个字段都要能对上号，所以这里不做任何省略 —— λmax、CI、RI、CR 都留着。
 */
public record AhpResult(
        double[] weights,      // 归一化后的权重向量 W，长度 = Criterion.count()
        double lambdaMax,      // 最大特征根 λmax
        double consistencyIndex,   // 一致性指标 CI = (λmax − n) / (n − 1)
        double consistencyRatio,   // 一致性比例 CR = CI / RI
        double randomIndex,    // 随机一致性指标 RI，n 对应 Saaty 对照表
        boolean passed         // CR < 0.1 才算通过
) {

    public AhpResult {
        weights = weights.clone();   // record 里的数组要防御性拷贝，否则外部能改
    }

    @Override
    public double[] weights() {
        return weights.clone();
    }

    /** 按准则取权重 */
    public double weightOf(Criterion c) {
        return weights[c.ordinal()];
    }

    @Override
    public String toString() {
        return "λmax=%.4f  CI=%.4f  CR=%.4f  %s  W=%s".formatted(
                lambdaMax, consistencyIndex, consistencyRatio,
                passed ? "一致性通过" : "一致性不通过",
                Arrays.toString(Arrays.stream(weights).mapToObj(w -> "%.4f".formatted(w)).toArray()));
    }
}
