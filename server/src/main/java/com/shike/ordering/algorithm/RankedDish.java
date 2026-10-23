package com.shike.ordering.algorithm;

/**
 * TOPSIS 排序结果里的一道菜。
 *
 * <p>除了名次，还带上 D⁺、D⁻ 和贴近度 C —— 论文第 3.4 节的表 3.4「TOPSIS 计算结果示例」
 * 要的就是这几列，界面上的「推荐实验室」页把它们原样展示出来。
 *
 * @param dish      菜品
 * @param raw       五项准则的原始值
 * @param dPlus     到正理想解的欧氏距离，越小越好
 * @param dMinus    到负理想解的距离，越大越好
 * @param closeness 贴近度 C = D⁻ / (D⁺ + D⁻)，落在 0–1，按它降序排
 * @param rank      名次，从 1 开始
 */
public record RankedDish(
        DishFeature dish,
        double[] raw,
        double dPlus,
        double dMinus,
        double closeness,
        int rank
) {

    public RankedDish {
        raw = raw.clone();
    }

    @Override
    public double[] raw() {
        return raw.clone();
    }

    public double raw(Criterion c) {
        return raw[c.ordinal()];
    }

    /**
     * 把推荐结果翻译成人能读懂的一句话 —— 这是 AHP-TOPSIS 相对协同过滤的核心优势，
     * 不能只给一个名次就完事。论文摘要和结论都强调这一点。
     */
    public String explain() {
        return "按你的口味排第 %d 名：口味匹配 %.0f 分、%d 元、热度口碑 %.0f 分、出餐 %d 分钟，综合贴近度 %.4f"
                .formatted(rank,
                        raw(Criterion.TASTE),
                        (int) raw(Criterion.PRICE),
                        raw(Criterion.HOT),
                        (int) raw(Criterion.WAIT),
                        closeness);
    }
}
