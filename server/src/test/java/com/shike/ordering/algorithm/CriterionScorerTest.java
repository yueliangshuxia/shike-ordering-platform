package com.shike.ordering.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 五个准则的换算逻辑。
 *
 * <p>重点盯三处容易写错的地方：热度为什么要开方、成本型的方向、忌口的别名匹配。
 */
class CriterionScorerTest {

    // ---------- 口味匹配 ----------

    @Test
    @DisplayName("没有任何偏好信息的新客，口味分是基准值，不崩也不为 0")
    void firstTimeVisitorGetsBaselineTasteScore() {
        DishFeature dish = dish("麻婆豆腐", "stirfry", 32, 2, Set.of("招牌"), 1673, 4.8, 8, List.of("嫩豆腐"));

        double score = CriterionScorer.tasteScore(dish, UserTaste.firstTimeVisitor());

        // 基准 40，再按辣度贴合度加减。关键是不能是 0，
        // 否则新客的推荐会被「口味匹配」这一项直接压死，冷启动优势就没了
        assertThat(score).isGreaterThan(0).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("命中偏好分类加分")
    void preferredCategoryBoostsTasteScore() {
        DishFeature dish = dish("麻婆豆腐", "stirfry", 32, 1, Set.of(), 100, 4.5, 8, List.of());
        UserTaste neutral = new UserTaste(Map.of(), 3, false, Set.of());
        UserTaste likesStirfry = new UserTaste(Map.of("stirfry", 1.0), 3, false, Set.of());

        assertThat(CriterionScorer.tasteScore(dish, likesStirfry))
                .isCloseTo(CriterionScorer.tasteScore(dish, neutral) + 34, within(1e-9));
    }

    @Test
    @DisplayName("超过辣度上限的菜会被明显压下去")
    void overSpiceLimitIsPenalised() {
        DishFeature extraHot = dish("特辣火锅", "hotpot", 62, 3, Set.of(), 100, 4.5, 14, List.of());
        UserTaste mild = new UserTaste(Map.of("hotpot", 1.0), 0, true, Set.of());

        double penalised = CriterionScorer.tasteScore(extraHot, mild);
        double withSamePrefsButNoLimit = CriterionScorer.tasteScore(extraHot,
                new UserTaste(Map.of("hotpot", 1.0), 3, false, Set.of()));

        assertThat(penalised).isLessThan(withSamePrefsButNoLimit);
    }

    @Test
    @DisplayName("避开的食材每命中一项扣一档")
    void avoidedIngredientDeductsPerHit() {
        // 同时含葱和蒜
        DishFeature dish = dish("蒜香排骨", "stirfry", 48, 1, Set.of(), 100, 4.5, 10, List.of("猪肋排", "蒜", "葱"));

        double noAvoid = CriterionScorer.tasteScore(dish, new UserTaste(Map.of(), 3, false, Set.of()));
        double oneAvoid = CriterionScorer.tasteScore(dish, new UserTaste(Map.of(), 3, false, Set.of("蒜")));
        double twoAvoid = CriterionScorer.tasteScore(dish, new UserTaste(Map.of(), 3, false, Set.of("蒜", "葱")));

        // 这道菜基准分 55，两项忌口要扣 60，扣穿了 —— 会被夹到 0
        assertThat(noAvoid).isEqualTo(55);
        assertThat(oneAvoid).isEqualTo(25);
        assertThat(twoAvoid).isZero();
        // 扣分是单调的：忌口勾得越多，分只会更低，不会反过来涨
        assertThat(noAvoid).isGreaterThan(oneAvoid);
        assertThat(oneAvoid).isGreaterThan(twoAvoid);
    }

    @Test
    @DisplayName("忌口支持别名：勾「牛羊肉」能挡住「牛肉末」")
    void avoidAliasMatchesRelatedIngredients() {
        DishFeature beef = dish("麻婆豆腐", "stirfry", 32, 2, Set.of(), 100, 4.5, 8,
                List.of("嫩豆腐", "牛肉末", "郫县豆瓣"));

        assertThat(CriterionScorer.avoidHits(beef, Set.of("牛羊肉"))).isEqualTo(1);
        assertThat(CriterionScorer.avoidHits(beef, Set.of("香菜"))).isZero();
    }

    @Test
    @DisplayName("口味分被限制在 0–100，不会出现负数")
    void tasteScoreIsClamped() {
        // 又超辣度上限、又命中两项忌口，原始扣分足以跌破 0
        DishFeature dish = dish("重口菜", "barbecue", 68, 3, Set.of(), 100, 4.0, 12,
                List.of("牛", "羊", "葱"));
        UserTaste taste = new UserTaste(Map.of(), 0, true, Set.of("牛羊肉", "葱"));

        assertThat(CriterionScorer.tasteScore(dish, taste)).isBetween(0.0, 100.0);
    }

    // ---------- 热度口碑 ----------

    @Test
    @DisplayName("销量开方：销量翻两番，销量项的贡献只翻一番")
    void salesAreSquareRootCompressed() {
        // 两道菜只差销量，评分相同 —— 这样分数差完全来自销量项。
        //
        // 这两个数必须落在当前 SOLD_BENCHMARK（1700）以下。销量一旦超过基准值，
        // soldPart 就超过 100，而 hotScore 最后会 min(100, ...) 封顶 ——
        // 封顶之后比值就不再是 2 倍了（实测 4000/1000 会得到 1.57）。
        // 这是个真实存在、而且很容易踩的边界：基准值取"菜单里最高销量"时，
        // 任何超过它的菜都会被封顶，压缩效果和预期不一样。
        DishFeature modest = dish("姜葱捞面", "congee", 22, 0, Set.of(), 400, 4.0, 7, List.of());
        DishFeature popular = dish("明档鲜肉包", "dimsum", 12, 0, Set.of(), 1600, 4.0, 8, List.of());

        double ratio = soldContribution(popular) / soldContribution(modest);

        // 销量 4 倍，开方后销量项只到 2 倍。若直接用量销，这里会是 4 倍 ——
        // 那正是「一个月卖几千份的爆款把所有点心挤出榜单」的成因。
        // 这是论文创新点之二，数值必须锁住。
        assertThat(ratio).isCloseTo(2.0, within(0.01));
    }

    @Test
    @DisplayName("销量远超基准值时热度分封顶，不会溢出也不会倒挂")
    void aboveBenchmarkIsCappedNotInverted() {
        // 销量涨到基准值的 100 倍，热度分也只到 100，不溢出
        DishFeature monster = dish("爆款", "dimsum", 20, 0, Set.of(), 170_000, 5.0, 8, List.of());
        assertThat(CriterionScorer.hotScore(monster)).isLessThanOrEqualTo(100.0);

        // 而且必须仍然满足单调性：卖得多的不会比卖得少的分低。
        // 封顶带来的是"打平"，不该出现倒挂 —— 倒挂会让排序出现无法解释的结果
        DishFeature atBenchmark = dish("基准", "dimsum", 20, 0, Set.of(), 1700, 5.0, 8, List.of());
        assertThat(CriterionScorer.hotScore(monster))
                .isGreaterThanOrEqualTo(CriterionScorer.hotScore(atBenchmark));
    }

    @Test
    @DisplayName("热度分封顶 100，超出基准销量的菜不会溢出")
    void hotScoreIsCapped() {
        DishFeature monster = dish("爆款", "barbecue", 15, 1, Set.of(), 999_999, 5.0, 10, List.of());

        assertThat(CriterionScorer.hotScore(monster)).isLessThanOrEqualTo(100.0);
    }

    // ---------- 清淡健康 ----------

    @Test
    @DisplayName("不辣且素食得分最高，辣和冰饮都会被扣")
    void healthyScoreReflectsSpiceAndVegetarian() {
        DishFeature veg = dish("时蔬沙拉", "cold", 26, 0, Set.of("素食"), 421, 4.4, 6, List.of());
        DishFeature spicy = dish("麻辣香锅", "hotpot", 78, 3, Set.of(), 300, 4.6, 16, List.of());
        DishFeature iced = dish("酸梅汤", "drink", 14, 0, Set.of("冰饮"), 1980, 4.5, 3, List.of());

        assertThat(CriterionScorer.healthyScore(veg)).isGreaterThan(100.0 - 1);   // 100 基础 + 素食 12，封顶 100
        assertThat(CriterionScorer.healthyScore(spicy))
                .isLessThan(CriterionScorer.healthyScore(veg));
        // 冰饮比同辣度的普通菜低 6 分
        assertThat(CriterionScorer.healthyScore(iced))
                .isCloseTo(100.0 - 6, within(1e-9));
    }

    @Test
    @DisplayName("清淡分也被限制在 0–100")
    void healthyScoreIsClamped() {
        DishFeature verySpicy = dish("变态辣", "hotpot", 78, 3, Set.of("冰饮"), 100, 4.0, 16, List.of());

        assertThat(CriterionScorer.healthyScore(verySpicy)).isBetween(0.0, 100.0);
    }

    // ---------- 决策矩阵 ----------

    @Test
    @DisplayName("决策矩阵的列顺序与 Criterion 的声明顺序一致")
    void scoreArrayFollowsCriterionOrder() {
        DishFeature dish = dish("测试菜", "dimsum", 30, 0, Set.of(), 500, 4.5, 7, List.of());

        double[] raw = CriterionScorer.score(dish, UserTaste.firstTimeVisitor());

        assertThat(raw).hasSize(Criterion.count());
        assertThat(raw[Criterion.PRICE.ordinal()]).isEqualTo(30.0);
        assertThat(raw[Criterion.WAIT.ordinal()]).isEqualTo(7.0);
    }

    @Test
    @DisplayName("忌口不误伤形近食材：红腰豆不是内脏，牛肝菌也不是")
    void avoidDoesNotFlagLookAlikeIngredients() {
        // 这两个都是菜单里真实存在的菜，也是实际被误判过的两个
        DishFeature salad = dish("田园时蔬沙拉", "cold", 26, 0, Set.of("素食"), 421, 4.4, 6,
                List.of("罗马生菜", "小番茄", "红腰豆"));
        DishFeature noodles = dish("菌油拌面", "staple", 24, 0, Set.of(), 700, 4.5, 8,
                List.of("碱水面", "牛肝菌", "鸡枞菌"));

        assertThat(CriterionScorer.avoidHits(salad, Set.of("内脏"))).isZero();
        assertThat(CriterionScorer.avoidHits(noodles, Set.of("内脏"))).isZero();
    }

    @Test
    @DisplayName("形近名单放行的同时，真内脏照样拦得住")
    void realOffalStillMatches() {
        // 只验证"没误伤"，会让实现退化成"干脆不匹配"也能过。
        // 这条用例堵住那个口子
        DishFeature kidney = dish("爆炒腰花", "stirfry", 46, 2, Set.of(), 900, 4.5, 10,
                List.of("猪腰", "青椒"));
        DishFeature tripe = dish("毛肚火锅", "hotpot", 68, 3, Set.of(), 800, 4.4, 15,
                List.of("毛肚", "鸭血", "豆芽"));

        assertThat(CriterionScorer.avoidHits(kidney, Set.of("内脏"))).isEqualTo(1);
        assertThat(CriterionScorer.avoidHits(tripe, Set.of("内脏"))).isEqualTo(1);
        // 牛羊肉那组不受这张名单影响
        assertThat(CriterionScorer.avoidHits(tripe, Set.of("牛羊肉"))).isZero();
    }

    // ---------- 测试夹具 ----------

    /** 从热度总分里减掉评分那一半，剩下的就是销量项的贡献 */
    private static double soldContribution(DishFeature d) {
        return CriterionScorer.hotScore(d) - d.rating() / 5.0 * 100.0 * 0.5;
    }

    static DishFeature dish(String name, String cat, int price, int spice, Set<String> tags,
                            int sold, double rating, int prep, List<String> ingredients) {
        return new DishFeature(1L, name, cat, price, spice, tags, sold, rating, prep, ingredients);
    }
}
