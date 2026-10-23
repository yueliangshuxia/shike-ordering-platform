package com.shike.ordering.algorithm;

/**
 * 层次分析法（AHP）：由判断矩阵求各评价准则的权重，并做一致性检验。
 *
 * <p>用方根法（几何平均）求特征向量，而不是幂迭代法 —— 判断矩阵只有 5 阶，
 * 方根法手算能复核，答辩时评委让你现场推一遍也推得出来。这也是论文第 3.3 节写的那套。
 *
 * <p>计算步骤：
 * <ol>
 *   <li>每行求几何平均 {@code g_i = (∏ a_ij)^(1/n)}</li>
 *   <li>归一化得权重 {@code w_i = g_i / Σ g}</li>
 *   <li>最大特征根 {@code λmax = Σ (AW)_i / (n · w_i)}</li>
 *   <li>一致性检验 {@code CI = (λmax − n)/(n − 1)}，{@code CR = CI / RI}</li>
 * </ol>
 */
public final class AhpCalculator {

    private AhpCalculator() {}

    /**
     * 随机一致性指标 RI，Saaty 给的对照表。
     * 下标即矩阵阶数 n；n ≤ 2 时判断矩阵必然一致，RI 记 0，CR 无意义。
     */
    private static final double[] RI = {0.0, 0.0, 0.0, 0.58, 0.90, 1.12, 1.24, 1.32, 1.41, 1.45, 1.49};

    /**
     * 本系统的默认判断矩阵，由店铺运营者按经营经验填写。
     *
     * <p>行列顺序与 {@link Criterion} 一致：口味 / 价格 / 热度 / 等待 / 清淡。
     * {@code a[i][j]} 表示准则 i 相对准则 j 的重要程度，取 1–9 标度，
     * 且必须满足 {@code a[i][j] = 1 / a[j][i]}。
     */
    public static final double[][] DEFAULT_JUDGMENT = {
            {1.0,     2.0,    2.0,    3.0,    5.0},
            {1.0 / 2, 1.0,    2.0,    2.0,    4.0},
            {1.0 / 2, 1.0 / 2, 1.0,   2.0,    4.0},
            {1.0 / 3, 1.0 / 2, 1.0 / 2, 1.0,  2.0},
            {1.0 / 5, 1.0 / 4, 1.0 / 4, 1.0 / 2, 1.0}
    };

    /** 用默认判断矩阵算一遍 */
    public static AhpResult calculate() {
        return calculate(DEFAULT_JUDGMENT);
    }

    /**
     * @param matrix 判断矩阵，必须是非空方阵且阶数不超过 RI 表覆盖的范围
     */
    public static AhpResult calculate(double[][] matrix) {
        validate(matrix);
        int n = matrix.length;

        // 1. 每行求几何平均（方根法）
        double[] geo = new double[n];
        for (int i = 0; i < n; i++) {
            double product = 1.0;
            for (int j = 0; j < n; j++) {
                product *= matrix[i][j];
            }
            geo[i] = Math.pow(product, 1.0 / n);
        }

        // 2. 归一化得到权重向量 W
        double sum = 0.0;
        for (double g : geo) {
            sum += g;
        }
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            weights[i] = geo[i] / sum;
        }

        // 3. 最大特征根 λmax = Σ (AW)_i / (n · w_i)
        double lambdaMax = 0.0;
        for (int i = 0; i < n; i++) {
            double aw = 0.0;
            for (int j = 0; j < n; j++) {
                aw += matrix[i][j] * weights[j];
            }
            lambdaMax += aw / (n * weights[i]);
        }

        // 4. 一致性检验
        double ci = (lambdaMax - n) / (n - 1.0);
        double ri = RI[n];
        // n ≤ 2 时 RI 为 0，判断矩阵必定完全一致，直接判通过
        double cr = ri == 0.0 ? 0.0 : ci / ri;

        return new AhpResult(weights, lambdaMax, ci, cr, ri, cr < 0.1);
    }

    private static void validate(double[][] matrix) {
        if (matrix == null || matrix.length == 0) {
            throw new IllegalArgumentException("判断矩阵不能为空");
        }
        int n = matrix.length;
        if (n < 3) {
            throw new IllegalArgumentException("判断矩阵至少要 3 阶，否则一致性检验没有意义");
        }
        if (n >= RI.length) {
            throw new IllegalArgumentException("判断矩阵阶数 " + n + " 超出 RI 对照表范围");
        }
        for (double[] row : matrix) {
            if (row.length != n) {
                throw new IllegalArgumentException("判断矩阵必须是方阵");
            }
        }
        for (int i = 0; i < n; i++) {
            if (Math.abs(matrix[i][i] - 1.0) > 1e-9) {
                throw new IllegalArgumentException("判断矩阵对角线必须全为 1，第 " + (i + 1) + " 个是 " + matrix[i][i]);
            }
            for (int j = i + 1; j < n; j++) {
                // 互反性：a_ij 与 a_ji 必须互为倒数，不满足的话权重方向就没有意义
                if (Math.abs(matrix[i][j] * matrix[j][i] - 1.0) > 1e-9) {
                    throw new IllegalArgumentException(
                            "判断矩阵不满足互反性：a[%d][%d]=%s 与 a[%d][%d]=%s 不是倒数"
                                    .formatted(i, j, matrix[i][j], j, i, matrix[j][i]));
                }
            }
        }
    }
}
