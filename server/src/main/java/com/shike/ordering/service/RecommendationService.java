package com.shike.ordering.service;

import com.shike.ordering.algorithm.Criterion;
import com.shike.ordering.algorithm.CriterionScorer;
import com.shike.ordering.algorithm.DishFeature;
import com.shike.ordering.algorithm.RankedDish;
import com.shike.ordering.algorithm.RecommendationEngine;
import com.shike.ordering.algorithm.TopsisResult;
import com.shike.ordering.algorithm.UserTaste;
import com.shike.ordering.common.BizException;
import com.shike.ordering.common.ErrorCode;
import com.shike.ordering.dto.RecommendRequest;
import com.shike.ordering.dto.RecommendResponse;
import com.shike.ordering.dto.RecommendResponse.CriterionVO;
import com.shike.ordering.dto.RecommendResponse.RecommendedDishVO;
import com.shike.ordering.entity.Dish;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 推荐接口的服务层：取数、调算法、把结果翻译成人话。
 *
 * <p>算法本身在 {@code com.shike.ordering.algorithm} 包里，这里只做三件事，
 * 而且刻意不让这三件事渗进算法包：
 *
 * <ol>
 *   <li><b>取候选集</b>。哪些菜能参与推荐是业务问题（下架的、售罄的、只要烧烤的），
 *       不是数学问题。</li>
 *   <li><b>准备用户画像</b>。要合并显式偏好和历史订单，这要查库。</li>
 *   <li><b>生成推荐理由</b>。这是本方案相对协同过滤最大的卖点 ——
 *       协同过滤只能给出"和你相似的人还买了"，而这里每一步都能说出为什么。</li>
 * </ol>
 */
@Service
public class RecommendationService {

    /** 判定"便宜/出餐快/热门"的分位线。前 25% 才值得说一句 */
    private static final double TOP_QUARTILE = 0.25;

    private final DishService dishService;
    private final TasteService tasteService;

    public RecommendationService(DishService dishService, TasteService tasteService) {
        this.dishService = dishService;
        this.tasteService = tasteService;
    }

    /**
     * AHP 默认权重。
     *
     * <p><b>这里才是全项目唯一适合加缓存的地方</b>：它是个纯函数，
     * 只依赖写死在代码里的判断矩阵，永远不会有"改了数据但缓存没刷新"的问题。
     * 反过来，菜品和推荐结果都没加缓存 —— 管理员改一次库存却看到旧菜单，
     * 是比多跑几次微秒级计算严重得多的事故。
     */
    @Cacheable("ahpWeights")
    public double[] defaultWeights() {
        return RecommendationEngine.defaultWeights();
    }

    public RecommendResponse recommend(Long userId, RecommendRequest req) {
        RecommendRequest r = req == null
                ? new RecommendRequest(null, null, null, null, null, null, null)
                : req;

        // ---- 1. 候选集 ----
        // 菜品和它的特征只取一次。后面组装响应时还要用到菜品的图片、库存等字段，
        // 再从库里捞一遍不但多几次查询，更要命的是两次结果未必一致 ——
        // 中间刚好有人下了一单把最后一份买走，推荐结果里就会出现一个已经售罄的菜
        List<Dish> all = dishService.availableDishes();
        if (all.isEmpty()) {
            return emptyResponse(resolveWeights(r));
        }
        Map<Long, Dish> dishById = all.stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity(), (a, b) -> a));

        List<DishFeature> features = dishService.toFeatures(all);
        if (r.category() != null && !r.category().isBlank()) {
            features = filterByCategory(features, r.category());
        }
        if (features.isEmpty()) {
            return emptyResponse(resolveWeights(r));
        }

        // ---- 2. 画像。请求里有覆盖项就用覆盖项，否则按登录人算 ----
        UserTaste taste = resolveTaste(userId, r);

        // ---- 3. 权重 ----
        double[] weights = resolveWeights(r);

        // ---- 4. 交给算法 ----
        TopsisResult result = RecommendationEngine.recommend(features, taste, weights);

        // ---- 5. 组装 ----
        return assemble(result, dishById, taste, r);
    }

    // ------------------------------------------------------------------
    // 入参处理
    // ------------------------------------------------------------------

    private List<DishFeature> filterByCategory(List<DishFeature> features, String categoryCode) {
        if (DishFeature.CAT_SIGNATURE.equals(categoryCode)) {
            // 虚拟分类：按「招牌」标签筛，而不是按分类列筛 ——
            // 数据库里根本没有 category 为 signature 的菜
            return features.stream().filter(f -> f.hasTag(DishFeature.TAG_SIGNATURE)).toList();
        }
        var c = dishService.findByCode(categoryCode);
        if (c == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND, "分类不存在：" + categoryCode);
        }
        return features.stream().filter(f -> c.getCode().equals(f.categoryId())).toList();
    }

    private UserTaste resolveTaste(Long userId, RecommendRequest r) {
        boolean hasOverride = r.preferCategories() != null || r.maxSpice() != null
                || r.avoidSpice() != null || r.avoidIngredients() != null;
        if (!hasOverride) {
            return tasteService.resolve(userId);
        }

        UserTaste base = tasteService.resolve(userId);
        Map<String, Double> prefs = new HashMap<>(base.preferredCategories());
        if (r.preferCategories() != null) {
            prefs = r.preferCategories().stream()
                    .collect(Collectors.toMap(c -> c, c -> 0.6, (a, b) -> a, HashMap::new));
        }
        return new UserTaste(
                prefs,
                r.maxSpice() == null ? base.maxSpice() : r.maxSpice(),
                r.avoidSpice() == null ? base.avoidSpice() : r.avoidSpice(),
                r.avoidIngredients() == null ? base.avoidIngredients() : new java.util.HashSet<>(r.avoidIngredients()));
    }

    /**
     * 权重归一化。
     *
     * <p>滑杆传上来的是原始值（比如 0–100 的整数），这里统一化成和为 1。
     * 归一化放在服务端而不是让前端算：前端要是漏了这一步，
     * TOPSIS 的加权距离会整体被放大，排序结果其实不变 ——
     * 但展示出来的 V⁺/V⁻ 就完全对不上论文里的数了，很难查。
     *
     * <p>全传 0 会退化成等权重，而不是除零崩溃：用户把五根滑杆全拖到底
     * 是个合理操作，不该报错。
     */
    private double[] resolveWeights(RecommendRequest r) {
        if (r.weights() == null || r.weights().isEmpty()) {
            return defaultWeights();
        }
        if (r.weights().size() != Criterion.count()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "权重需要 " + Criterion.count() + " 个，收到 " + r.weights().size() + " 个");
        }
        double[] w = new double[Criterion.count()];
        double sum = 0;
        for (int i = 0; i < w.length; i++) {
            Double v = r.weights().get(i);
            if (v == null || v < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "权重不能为负");
            }
            w[i] = v;
            sum += v;
        }
        if (sum <= 0) {
            return RecommendationEngine.uniformWeights();
        }
        for (int i = 0; i < w.length; i++) {
            w[i] /= sum;
        }
        return w;
    }

    // ------------------------------------------------------------------
    // 组装响应
    // ------------------------------------------------------------------

    private RecommendResponse assemble(TopsisResult result, Map<Long, Dish> dishById,
                                       UserTaste taste, RecommendRequest r) {
        int limit = r.topK() == null || r.topK() <= 0
                ? result.ranked().size()
                : Math.min(r.topK(), result.ranked().size());

        // 用来判断"便宜/快/热门"的分布。基于全部候选算，不是基于展示的前 k 名 ——
        // 少算几个菜就会把"全场最便宜"说成"比较便宜"
        Distribution dist = Distribution.of(result.ranked());

        List<RecommendedDishVO> dishes = new ArrayList<>();
        for (RankedDish rd : result.ranked().subList(0, limit)) {
            Dish d = dishById.get(rd.dish().id());
            if (d == null) {
                continue;
            }
            dishes.add(new RecommendedDishVO(
                    d.getId(), d.getCode(), d.getName(),
                    rd.dish().categoryId(), null,
                    d.getPrice(), d.getImg(), d.getBrief(),
                    List.copyOf(rd.dish().tags()),
                    d.getSpice(), d.getPrepMin(), d.getSold(), d.getRating(),
                    d.getStock(), d.soldOut(),
                    rd.rank(), rd.closeness(), rd.dPlus(), rd.dMinus(),
                    scoresOf(rd),
                    reasonsFor(rd, taste, dist),
                    caveatsFor(rd, taste, dist)));
        }

        return new RecommendResponse(
                criterionValues(result.weights()),
                criterionValues(result.ideal()),
                criterionValues(result.anti()),
                dishes);
    }

    private RecommendResponse emptyResponse(double[] weights) {
        return new RecommendResponse(
                criterionValues(weights),
                criterionValues(new double[Criterion.count()]),
                criterionValues(new double[Criterion.count()]),
                List.of());
    }

    private List<CriterionVO> scoresOf(RankedDish rd) {
        List<CriterionVO> list = new ArrayList<>();
        for (Criterion c : Criterion.values()) {
            list.add(new CriterionVO(c.key(), c.label(), c.unit(), c.benefit(), rd.raw(c)));
        }
        return list;
    }

    private List<CriterionVO> criterionValues(double[] values) {
        List<CriterionVO> list = new ArrayList<>();
        for (Criterion c : Criterion.values()) {
            list.add(new CriterionVO(c.key(), c.label(), c.unit(), c.benefit(), values[c.ordinal()]));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // 推荐理由 —— 这个项目最该做好的部分
    // ------------------------------------------------------------------

    /**
     * 说清楚「为什么推荐这道菜」。
     *
     * <p>协同过滤给出的理由只能是「和你口味相似的人还点了这些」，
     * 用户没法验证，也没法反驳。而这里每条理由都对应一个具体的数值：
     * 便宜，是因为它 ¥26；出餐快，是因为它 6 分钟。
     * 这是论文摘要里「有依据、看得懂理由」那句话的落点。
     */
    private List<String> reasonsFor(RankedDish rd, UserTaste taste, Distribution dist) {
        DishFeature d = rd.dish();
        List<String> reasons = new ArrayList<>();

        if (taste.likes(d.categoryId())) {
            reasons.add("属于你偏好的口味分类");
        }
        if (taste.likesSignature() && d.hasTag(DishFeature.TAG_SIGNATURE)) {
            reasons.add("招牌菜");
        }

        double pricePct = dist.percentile(Criterion.PRICE, rd.raw(Criterion.PRICE));
        if (pricePct <= TOP_QUARTILE) {
            reasons.add("价格实惠：¥" + trimPrice(rd.raw(Criterion.PRICE))
                    + "，比全场约 " + Math.round((1 - pricePct) * 100) + "% 的菜便宜");
        }

        double waitPct = dist.percentile(Criterion.WAIT, rd.raw(Criterion.WAIT));
        if (waitPct <= TOP_QUARTILE) {
            reasons.add("出餐快：约 " + (int) rd.raw(Criterion.WAIT) + " 分钟");
        }

        double hotPct = dist.percentile(Criterion.HOT, rd.raw(Criterion.HOT));
        if (hotPct >= 1 - TOP_QUARTILE) {
            reasons.add("人气高：近 30 天卖出 " + d.monthlySold() + " 份，评分 "
                    + trimRating(d.rating()));
        }

        if (d.hasTag(DishFeature.TAG_VEGETARIAN) && !taste.avoidSpice()) {
            reasons.add("素食，口味清淡");
        }

        if (reasons.isEmpty()) {
            // 一项突出的都没有也不能空着。这时说实话：
            // 它是靠"哪项都不差"上来的，这正是 TOPSIS 综合排序的意义
            reasons.add("各项表现均衡，综合贴近度 " + round4(rd.closeness()));
        }
        return reasons;
    }

    /**
     * 扣分项。
     *
     * <p>刻意把不利因素也说出来。用户看到一道带内脏的菜排在前头却没被告知，
     * 只会觉得"这算法不准"；说清楚"它本来该更靠前，是因为你忌口内脏才被压下来"，
     * 用户反而会认可这套逻辑真的在为他考虑。
     */
    private List<String> caveatsFor(RankedDish rd, UserTaste taste, Distribution dist) {
        DishFeature d = rd.dish();
        List<String> caveats = new ArrayList<>();

        int hits = CriterionScorer.avoidHits(d, taste.avoidIngredients());
        if (hits > 0) {
            // 把命中的具体食材找出来，别只说"含忌口食材" —— 用户想知道是哪个
            List<String> matched = d.ingredients().stream()
                    .filter(i -> taste.avoidIngredients().stream()
                            .anyMatch(a -> CriterionScorer.matchesIngredient(i, a)))
                    .toList();
            caveats.add("含你忌口的食材：" + String.join("、", matched));
        }

        if (d.spice() > taste.maxSpice()) {
            caveats.add("辣度 " + spiceLabel(d.spice()) + "，高于你设的上限");
        }

        if (dist.percentile(Criterion.PRICE, rd.raw(Criterion.PRICE)) >= 1 - TOP_QUARTILE) {
            caveats.add("价格偏高：¥" + trimPrice(rd.raw(Criterion.PRICE)));
        }
        return caveats;
    }

    private static String spiceLabel(int spice) {
        return switch (spice) {
            case 0 -> "不辣";
            case 1 -> "微辣";
            case 2 -> "中辣";
            default -> "特辣";
        };
    }

    private static String trimPrice(double v) {
        return new BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static String trimRating(double v) {
        return new BigDecimal(v).setScale(1, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    /**
     * 某一项准则在全部候选里的分布，用来判断"这道菜这项算不算突出"。
     *
     * <p>用分位而不是简单的"比平均分高"：价格这类数据往往是长尾的，
     * 均值会被少数贵的菜拉偏，说「低于平均价」可能全场八成菜都符合，毫无信息量。
     */
    private record Distribution(Map<Criterion, double[]> sortedAsc) {

        static Distribution of(List<RankedDish> ranked) {
            Map<Criterion, double[]> map = new LinkedHashMap<>();
            for (Criterion c : Criterion.values()) {
                double[] values = ranked.stream()
                        .mapToDouble(rd -> rd.raw(c))
                        .sorted()
                        .toArray();
                map.put(c, values);
            }
            return new Distribution(map);
        }

        /** 有多少比例的候选在这个值以下。0 表示它是最小的，1 表示它是最大的 */
        double percentile(Criterion c, double value) {
            double[] values = sortedAsc.get(c);
            if (values == null || values.length == 0) {
                return 0.5;
            }
            int below = 0;
            for (double v : values) {
                if (v < value) {
                    below++;
                }
            }
            int equal = 0;
            for (double v : values) {
                if (v == value) {
                    equal++;
                }
            }
            // 并列时取中点，避免"所有菜同分"时集体被说成最便宜或最贵
            return (below + equal / 2.0) / values.length;
        }
    }

    /** 按销量取热销榜，首页「大家都在点」用 */
    public List<Dish> hotDishes(int limit) {
        return dishService.availableDishes().stream()
                .sorted(Comparator.comparing(Dish::getSold, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }
}
