package com.shike.ordering.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次推荐请求的完整结果。
 *
 * <p>除了排序，把 TOPSIS 的<b>中间过程一并返回</b>：权重、正负理想解、
 * 每道菜的 D⁺/D⁻/贴近度。理由有三：
 *
 * <ol>
 *   <li>「推荐实验室」页要把中间过程摊开展示，这是答辩演示的核心画面；</li>
 *   <li>论文表 3.4「TOPSIS 计算结果示例」要的就是这几列，
 *       直接从接口取数，不用手抄；</li>
 *   <li>老师追问「为什么 A 排在 B 前面」时，能当场调出两个距离来对比。</li>
 * </ol>
 *
 * @param weights  五项准则的权重，顺序与 {@code Criterion} 声明顺序一致
 * @param ideal    正理想解 V⁺
 * @param anti     负理想解 V⁻
 * @param dishes   排好序的推荐结果
 */
public record RecommendResponse(
        List<CriterionVO> weights,
        List<CriterionVO> ideal,
        List<CriterionVO> anti,
        List<RecommendedDishVO> dishes
) {

    /**
     * 一个准则上的数值。理想解和权重都用这个结构表达，
     * 前端就可以复用同一个渲染逻辑，不用为每种数组各写一套。
     */
    public record CriterionVO(String key, String label, String unit, boolean benefit, double value) {
    }

    /**
     * 一道推荐菜。
     *
     * @param closeness 贴近度 C，最终排序依据
     * @param reasons   推荐理由，按重要性排好序
     * @param caveats   扣分项。宁可把「因为它有内脏所以被压了」说出来，
     *                  也不要让用户看到一道讨厌的菜排在前头却不知道为什么
     */
    public record RecommendedDishVO(
            Long id,
            String code,
            String name,
            String categoryId,
            String categoryName,
            BigDecimal price,
            String img,
            String brief,
            List<String> tags,
            Integer spice,
            Integer prepMin,
            Integer sold,
            BigDecimal rating,
            Integer stock,
            boolean soldOut,
            int rank,
            double closeness,
            double dPlus,
            double dMinus,
            List<CriterionVO> scores,
            List<String> reasons,
            List<String> caveats
    ) {
    }
}
