package com.shike.ordering.algorithm;

/**
 * 菜品推荐的评价准则。
 *
 * <p>顺序就是判断矩阵的行列顺序，也是决策矩阵的列顺序 —— 用枚举而不是字符串数组，
 * 是为了让这三处不可能对不上。改顺序会同时影响 AHP 权重和 TOPSIS 的列，
 * 所以顺序一旦定下就不要动。
 *
 * <p>{@code benefit} 区分效益型与成本型：效益型越大越好（取列最大为正理想解），
 * 成本型越小越好（取列最小为正理想解）。方向搞反是这类论文最常见的错误，
 * 所以这里把它做成准则自身的属性，而不是散在计算逻辑里的 if。
 */
public enum Criterion {

    /** 口味匹配：菜品属性与用户偏好的契合程度，唯一需要用户画像的准则 */
    TASTE("taste", "口味匹配", "", true),

    /** 价格实惠：单价，成本型 */
    PRICE("price", "价格实惠", "元", false),

    /** 热度口碑：近 30 天销量与评分的综合 */
    HOT("hot", "热度口碑", "", true),

    /** 出餐速度：等待时长，成本型 */
    WAIT("wait", "出餐速度", "分", false),

    /** 清淡健康：辣度与荤素 */
    HEALTHY("healthy", "清淡健康", "", true);

    private final String key;
    private final String label;
    private final String unit;
    private final boolean benefit;

    Criterion(String key, String label, String unit, boolean benefit) {
        this.key = key;
        this.label = label;
        this.unit = unit;
        this.benefit = benefit;
    }

    public String key()   { return key; }
    public String label() { return label; }
    public String unit()  { return unit; }

    /** true 为效益型（越大越好），false 为成本型（越小越好） */
    public boolean benefit() { return benefit; }

    public static int count() { return values().length; }
}
