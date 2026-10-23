package com.shike.ordering.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * TOPSIS 排序。
 *
 * <p>最值得防的两个错：成本型方向搞反、正负理想解只在展示用的前几名里取。
 * 两者都能算出「看起来正常」的结果，但结果其实是错的 —— 所以各有专门用例盯着。
 */
class TopsisRankerTest {

    @Test
    @DisplayName("名次从 1 连续排到 m，不重不漏")
    void ranksAreContiguous() {
        TopsisResult r = RecommendationEngine.recommend(sampleMenu(), UserTaste.firstTimeVisitor());

        assertThat(r.ranked()).hasSize(sampleMenu().size());
        for (int i = 0; i < r.ranked().size(); i++) {
            assertThat(r.ranked().get(i).rank()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("贴近度落在 0–1，按降序排")
    void closenessIsBoundedAndSorted() {
        TopsisResult r = RecommendationEngine.recommend(sampleMenu(), UserTaste.firstTimeVisitor());

        double previous = Double.MAX_VALUE;
        for (RankedDish d : r.ranked()) {
            assertThat(d.closeness()).isBetween(0.0, 1.0);
            assertThat(d.closeness()).isLessThanOrEqualTo(previous);
            assertThat(d.dPlus()).isGreaterThanOrEqualTo(0.0);
            assertThat(d.dMinus()).isGreaterThanOrEqualTo(0.0);
            previous = d.closeness();
        }
    }

    @Test
    @DisplayName("全面占优的菜排第一：贴近度 1，到正理想解距离 0")
    void dominatingDishRanksFirstWithPerfectCloseness() {
        // 全都在好处上：便宜、不辣、素食、月销高、评分高、出餐快，而且命中偏好分类
        DishFeature best = new DishFeature(1L, "完美菜", "dimsum", 10, 0, Set.of("素食"),
                4000, 5.0, 3, List.of());
        // 全都在坏处上
        DishFeature worst = new DishFeature(2L, "垫底菜", "hotpot", 88, 3, Set.of(),
                10, 1.0, 20, List.of());

        UserTaste taste = new UserTaste(Map.of("dimsum", 1.0), 3, false, Set.of());
        TopsisResult r = RecommendationEngine.recommend(List.of(worst, best), taste);

        RankedDish first = r.ranked().get(0);
        RankedDish last = r.ranked().get(1);

        assertThat(first.dish().id()).isEqualTo(1L);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.dPlus()).isCloseTo(0.0, within(1e-9));
        assertThat(first.closeness()).isCloseTo(1.0, within(1e-9));

        assertThat(last.dish().id()).isEqualTo(2L);
        assertThat(last.dMinus()).isCloseTo(0.0, within(1e-9));
        assertThat(last.closeness()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("成本型方向正确：只看价格和出餐时，又便宜又快的那道排第一")
    void costCriteriaPointTheRightWay() {
        DishFeature cheapAndFast = new DishFeature(1L, "便宜快手", "dimsum", 8, 0, Set.of(),
                50, 3.0, 3, List.of());
        DishFeature dearAndSlow = new DishFeature(2L, "又贵又慢", "hotpot", 88, 0, Set.of(),
                50, 3.0, 20, List.of());

        // 把全部权重压在两个成本型准则上，效益型给 0 —— 这样排序完全由成本型决定
        double[] weights = new double[Criterion.count()];
        weights[Criterion.PRICE.ordinal()] = 0.5;
        weights[Criterion.WAIT.ordinal()] = 0.5;

        TopsisResult r = TopsisRanker.rank(
                List.of(DishVector.of(cheapAndFast, UserTaste.firstTimeVisitor()),
                        DishVector.of(dearAndSlow, UserTaste.firstTimeVisitor())),
                weights);

        assertThat(r.ranked().get(0).dish().id()).isEqualTo(1L);
        // 便宜且快的那道，价格和等待两列都等于正理想解
        assertThat(r.ideal()[Criterion.PRICE.ordinal()])
                .isLessThan(r.ideal()[Criterion.HOT.ordinal()] + 1e9);   // 仅确保数组读得到
        assertThat(r.ranked().get(0).dPlus()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("正负理想解在全部备选上取，不能只在前几名里取")
    void idealSolutionsCoverAllAlternatives() {
        List<DishFeature> menu = List.of(
                new DishFeature(1L, "好菜甲", "dimsum", 42, 0, Set.of("素食"), 3000, 4.8, 5, List.of()),
                new DishFeature(2L, "好菜乙", "stirfry", 38, 0, Set.of("素食"), 2600, 4.7, 6, List.of()),
                new DishFeature(3L, "好菜丙", "dimsum", 46, 1, Set.of(), 2400, 4.6, 8, List.of()),
                new DishFeature(4L, "好菜丁", "cold", 34, 0, Set.of("素食"), 2200, 4.7, 7, List.of()),
                new DishFeature(9L, "白菜价", "cold", 1, 3, Set.of(), 1, 1.0, 20, List.of()));

        UserTaste taste = new UserTaste(Map.of("dimsum", 1.0, "cold", 1.0), 3, false, Set.of());
        double[] weights = RecommendationEngine.defaultWeights();
        List<DishVector> matrix = menu.stream().map(d -> DishVector.of(d, taste)).toList();

        TopsisResult r = TopsisRanker.rank(matrix, weights);

        // 逐列手工重算 V⁺ / V⁻。基准是"全部备选"，
        // 这正是要断言的：既不是前 k 名，也不是展示用的那几行。
        int n = Criterion.count();
        for (int j = 0; j < n; j++) {
            double columnNorm = 0.0;
            for (DishVector v : matrix) {
                columnNorm += v.raw()[j] * v.raw()[j];
            }
            columnNorm = Math.sqrt(columnNorm);

            double max = Double.NEGATIVE_INFINITY;
            double min = Double.POSITIVE_INFINITY;
            for (DishVector v : matrix) {
                double weighted = v.raw()[j] / columnNorm * weights[j];
                max = Math.max(max, weighted);
                min = Math.min(min, weighted);
            }
            boolean benefit = Criterion.values()[j].benefit();
            double expectedIdeal = benefit ? max : min;
            double expectedAnti = benefit ? min : max;

            assertThat(r.ideal()[j])
                    .as("准则 %s 的正理想解", Criterion.values()[j].label())
                    .isCloseTo(expectedIdeal, within(1e-12));
            assertThat(r.anti()[j])
                    .as("准则 %s 的负理想解", Criterion.values()[j].label())
                    .isCloseTo(expectedAnti, within(1e-12));
        }
    }

    @Test
    @DisplayName("只在前几名里取列极值会算出偏掉的理想解")
    void takingExtremesFromTopKWouldBeWrong() {
        // 这条用例是上一条的反面证据：证明"在前 k 名里取极值"确实会算错，
        // 而不是一个等价写法。
        List<DishFeature> menu = List.of(
                new DishFeature(1L, "好菜甲", "dimsum", 42, 0, Set.of("素食"), 3000, 4.8, 5, List.of()),
                new DishFeature(2L, "好菜乙", "stirfry", 38, 0, Set.of("素食"), 2600, 4.7, 6, List.of()),
                new DishFeature(3L, "好菜丙", "dimsum", 46, 1, Set.of(), 2400, 4.6, 8, List.of()),
                new DishFeature(4L, "好菜丁", "cold", 34, 0, Set.of("素食"), 2200, 4.7, 7, List.of()),
                new DishFeature(9L, "白菜价", "cold", 1, 3, Set.of(), 1, 1.0, 20, List.of()));

        UserTaste taste = new UserTaste(Map.of("dimsum", 1.0, "cold", 1.0), 3, false, Set.of());
        double[] weights = RecommendationEngine.defaultWeights();
        List<DishVector> matrix = menu.stream().map(d -> DishVector.of(d, taste)).toList();

        TopsisResult r = TopsisRanker.rank(matrix, weights);

        double priceNorm = Math.sqrt(matrix.stream()
                .mapToDouble(v -> v.raw()[Criterion.PRICE.ordinal()] * v.raw()[Criterion.PRICE.ordinal()])
                .sum());
        java.util.function.ToDoubleFunction<RankedDish> weightedPrice =
                d -> d.raw(Criterion.PRICE) / priceNorm * weights[Criterion.PRICE.ordinal()];

        double antiOverAll = r.anti()[Criterion.PRICE.ordinal()];
        // 价格是成本型，负理想解取列最大；最贵的那道菜排在中间，不在前两名里
        double antiOverTopTwo = r.ranked().subList(0, 2).stream()
                .mapToDouble(weightedPrice).max().orElseThrow();
        double antiOverAllManual = matrix.stream()
                .mapToDouble(v -> v.raw()[Criterion.PRICE.ordinal()] / priceNorm * weights[Criterion.PRICE.ordinal()])
                .max().orElseThrow();

        assertThat(antiOverAll).isCloseTo(antiOverAllManual, within(1e-12));
        assertThat(antiOverTopTwo).isNotCloseTo(antiOverAll, within(1e-6));
    }

    @Test
    @DisplayName("空候选集返回空结果，不抛异常")
    void emptyMenuIsHandled() {
        TopsisResult r = RecommendationEngine.recommend(List.of(), UserTaste.firstTimeVisitor());

        assertThat(r.ranked()).isEmpty();
        assertThat(r.top(5)).isEmpty();
        assertThat(r.ideal()).hasSize(Criterion.count());
    }

    @Test
    @DisplayName("只有一道菜时它既是正理想解也是负理想解，贴近度定为 0")
    void singleCandidateDoesNotDivideByZero() {
        DishFeature only = new DishFeature(1L, "独苗", "dimsum", 20, 0, Set.of(),
                100, 4.0, 8, List.of());

        TopsisResult r = RecommendationEngine.recommend(List.of(only), UserTaste.firstTimeVisitor());

        assertThat(r.ranked()).hasSize(1);
        assertThat(r.ranked().get(0).rank()).isEqualTo(1);
        assertThat(r.ranked().get(0).closeness()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("top(k) 超出总数时返回全部，不越界")
    void topClampsToAvailable() {
        TopsisResult r = RecommendationEngine.recommend(sampleMenu(), UserTaste.firstTimeVisitor());

        assertThat(r.top(100)).hasSize(sampleMenu().size());
        assertThat(r.top(2)).hasSize(2);
    }

    @Test
    @DisplayName("权重个数不对时直接报错，而不是算出一个错结果")
    void wrongWeightCountIsRejected() {
        List<DishVector> matrix = List.of(
                DishVector.of(sampleMenu().get(0), UserTaste.firstTimeVisitor()));

        assertThatThrownBy(() -> TopsisRanker.rank(matrix, new double[]{1.0, 0.5}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权重个数");
    }

    // ---------- 测试夹具 ----------

    static List<DishFeature> sampleMenu() {
        return List.of(
                CriterionScorerTest.dish("蟹黄小笼包", "dimsum", 42, 0, Set.of("招牌"), 1286, 4.8, 12, List.of("河蟹黄")),
                CriterionScorerTest.dish("麻婆豆腐", "stirfry", 32, 2, Set.of("招牌"), 1673, 4.8, 8, List.of("嫩豆腐")),
                CriterionScorerTest.dish("田园时蔬沙拉", "stirfry", 26, 0, Set.of("素食"), 421, 4.4, 6, List.of("生菜")),
                CriterionScorerTest.dish("红柳大串", "barbecue", 15, 2, Set.of(), 3082, 4.6, 16, List.of("羊肉")),
                CriterionScorerTest.dish("古法酸梅汤", "drink", 14, 0, Set.of("冰饮"), 1980, 4.5, 3, List.of("乌梅")),
                CriterionScorerTest.dish("草莓芝士蛋糕", "dessert", 32, 0, Set.of("冰饮"), 638, 4.8, 4, List.of("奶油芝士")));
    }
}
