package com.shike.ordering.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * AHP 的验收标准只有两条：算得对、算得稳。
 *
 * <p>第一组用例锁死默认判断矩阵的数值 —— 这几个数就是论文表 3.3，
 * 也跟前端 `recommend.js` 里的实现逐位对齐。改任何一边导致对不上，测试会立刻红。
 */
class AhpCalculatorTest {

    @Test
    @DisplayName("默认判断矩阵：λmax / CI / CR 与前端实现一致")
    void defaultMatrixMatchesFrontend() {
        AhpResult r = AhpCalculator.calculate();

        // 这三个数论文里要原样列出来，也跟前端 demo/recommend.html 显示的一致
        assertThat(r.lambdaMax()).isCloseTo(5.084, within(0.001));
        assertThat(r.consistencyIndex()).isCloseTo(0.0210, within(0.0001));
        assertThat(r.consistencyRatio()).isCloseTo(0.0188, within(0.0001));
        assertThat(r.randomIndex()).isEqualTo(1.12);
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("权重和为 1，且大小顺序符合判断矩阵的意图")
    void weightsAreNormalisedAndOrdered() {
        double[] w = AhpCalculator.calculate().weights();

        assertThat(w).hasSize(Criterion.count());
        assertThat(sum(w)).isCloseTo(1.0, within(1e-9));

        // 判断矩阵里口味定得最重、清淡最轻，算出来的权重必须保持这个次序，
        // 否则说明方根法或者归一化写反了
        assertThat(w[Criterion.TASTE.ordinal()])
                .isGreaterThan(w[Criterion.PRICE.ordinal()]);
        assertThat(w[Criterion.PRICE.ordinal()])
                .isGreaterThan(w[Criterion.HOT.ordinal()]);
        assertThat(w[Criterion.HOT.ordinal()])
                .isGreaterThan(w[Criterion.WAIT.ordinal()]);
        assertThat(w[Criterion.WAIT.ordinal()])
                .isGreaterThan(w[Criterion.HEALTHY.ordinal()]);

        // 权重都落在 (0,1)
        for (double v : w) {
            assertThat(v).isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("完全一致的判断矩阵：CI 和 CR 都是 0")
    void perfectlyConsistentMatrixHasZeroConsistencyRatio() {
        // a_ij = w_i / w_j 构造出来的矩阵必然完全一致
        double[] w = {0.4, 0.3, 0.15, 0.1, 0.05};
        int n = w.length;
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m[i][j] = w[i] / w[j];
            }
        }

        AhpResult r = AhpCalculator.calculate(m);

        assertThat(r.consistencyIndex()).isCloseTo(0.0, within(1e-9));
        assertThat(r.consistencyRatio()).isCloseTo(0.0, within(1e-9));
        assertThat(r.lambdaMax()).isCloseTo(n, within(1e-9));
        // 好矩阵应当还原出构造它用的那组权重
        for (int i = 0; i < n; i++) {
            assertThat(r.weights()[i]).isCloseTo(w[i], within(1e-9));
        }
    }

    @Test
    @DisplayName("前后矛盾的判断矩阵会被一致性检验挡下来")
    void inconsistentMatrixFailsConsistencyCheck() {
        // A 比 B 重要、B 比 C 重要，却给 A 和 C 标成同等重要 —— 明显的循环矛盾
        double[][] m = {
                {1, 5, 1},
                {1.0 / 5, 1, 5},
                {1, 1.0 / 5, 1}
        };

        AhpResult r = AhpCalculator.calculate(m);

        assertThat(r.consistencyRatio()).isGreaterThanOrEqualTo(0.1);
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("不满足互反性的矩阵直接拒绝，而不是算出一个错的结果")
    void rejectsNonReciprocalMatrix() {
        double[][] bad = {
                {1, 3, 5},
                {1.0 / 3, 1, 5},
                {1.0 / 5, 1.0 / 3, 1}   // a[2][1] 写成 1/3，与 a[1][2]=5 相乘得 5/3，不互反
        };

        assertThatThrownBy(() -> AhpCalculator.calculate(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("互反");
    }

    @Test
    @DisplayName("对角线不为 1 的矩阵直接拒绝")
    void rejectsNonUnitDiagonal() {
        double[][] bad = {
                {1, 3, 5},
                {1.0 / 3, 2, 4},   // 对角线上的 2 是错的
                {1.0 / 5, 1.0 / 4, 1}
        };

        assertThatThrownBy(() -> AhpCalculator.calculate(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对角线");
    }

    @Test
    @DisplayName("空矩阵和非方阵直接拒绝")
    void rejectsMalformedMatrices() {
        assertThatThrownBy(() -> AhpCalculator.calculate(new double[0][]))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> AhpCalculator.calculate(new double[][]{{1, 2}, {1}}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static double sum(double[] a) {
        double s = 0;
        for (double v : a) {
            s += v;
        }
        return s;
    }
}
