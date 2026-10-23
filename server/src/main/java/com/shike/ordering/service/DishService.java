package com.shike.ordering.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shike.ordering.algorithm.DishFeature;
import com.shike.ordering.common.BizException;
import com.shike.ordering.common.ErrorCode;
import com.shike.ordering.dto.CategoryVO;
import com.shike.ordering.dto.DishVO;
import com.shike.ordering.entity.Category;
import com.shike.ordering.entity.Dish;
import com.shike.ordering.entity.DishIngredient;
import com.shike.ordering.entity.DishSpec;
import com.shike.ordering.entity.DishTag;
import com.shike.ordering.mapper.CategoryMapper;
import com.shike.ordering.mapper.DishIngredientMapper;
import com.shike.ordering.mapper.DishMapper;
import com.shike.ordering.mapper.DishSpecMapper;
import com.shike.ordering.mapper.DishTagMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜单的读取与组装。
 *
 * <p><b>这个类的核心职责是把「拼装」做对。</b>一道菜由四张表拼成
 * （本体 + 标签 + 食材 + 规格），最自然的写法是对每道菜查三次关联表，
 * 但菜单页一次要展示二十几道菜，那就成了六十多次查询。
 * 所以这里统一的做法是：一次把关联行全捞回来，在内存里按 dishId 分好组。
 * 下面每个 assemble 方法都是这个套路。
 */
@Service
public class DishService {

    /** 虚拟分类「招牌推荐」的 code。它不在 t_category 表里 */
    public static final String CODE_SIGNATURE = DishFeature.CAT_SIGNATURE;

    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;
    private final DishTagMapper tagMapper;
    private final DishIngredientMapper ingredientMapper;
    private final DishSpecMapper specMapper;

    public DishService(DishMapper dishMapper, CategoryMapper categoryMapper,
                       DishTagMapper tagMapper, DishIngredientMapper ingredientMapper,
                       DishSpecMapper specMapper) {
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.ingredientMapper = ingredientMapper;
        this.specMapper = specMapper;
    }

    // ------------------------------------------------------------------
    // 分类
    // ------------------------------------------------------------------

    /**
     * 分类列表。
     *
     * <p>「招牌推荐」是<b>拼出来的</b>，不是查出来的 —— 没有任何一道菜的
     * category 是 signature，「招牌」是打在菜品上的标签。
     * 演示版在这里踩过坑：直接把 t_category 查出来返回，
     * 结果页脚和首页都链到 {@code menu.html#signature}，点进去永远是空的。
     * 所以它必须排在最前面，而且带着正确的菜品数量。
     */
    public List<CategoryVO> listCategories() {
        List<Category> all = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getEnabled, 1)
                .orderByAsc(Category::getSort));

        // 只统计还有货的菜，和菜单页「已售罄」的置灰口径保持一致
        List<Dish> available = dishMapper.selectList(Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getEnabled, 1)
                .gt(Dish::getStock, 0));

        Map<Long, Long> perCategory = available.stream()
                .collect(Collectors.groupingBy(Dish::getCategoryId, Collectors.counting()));

        long signatureCount = countWithTag(available, DishFeature.TAG_SIGNATURE);

        List<CategoryVO> result = new ArrayList<>();
        result.add(new CategoryVO(CODE_SIGNATURE, "招牌推荐", "mdi:chef-hat", true, signatureCount));
        for (Category c : all) {
            result.add(new CategoryVO(c.getCode(), c.getName(), c.getIcon(), false,
                    perCategory.getOrDefault(c.getId(), 0L)));
        }
        return result;
    }

    private long countWithTag(List<Dish> dishes, String tag) {
        if (dishes.isEmpty()) {
            return 0;
        }
        List<Long> ids = dishes.stream().map(Dish::getId).toList();
        Set<Long> tagged = tagMapper.selectList(Wrappers.<DishTag>lambdaQuery()
                        .in(DishTag::getDishId, ids)
                        .eq(DishTag::getTag, tag))
                .stream().map(DishTag::getDishId).collect(Collectors.toSet());

        return dishes.stream().filter(d -> tagged.contains(d.getId())).count();
    }

    // ------------------------------------------------------------------
    // 菜品
    // ------------------------------------------------------------------

    /**
     * 按分类或标签取菜。
     *
     * @param categoryCode 分类 code。传 {@code signature} 时按「招牌」标签筛，
     *                     而不是按分类列筛 —— 这就是虚拟分类的全部含义
     * @param tag          额外按标签筛，如「素食」
     * @param includeSoldOut 是否带上售罄的菜。菜单页要（置灰展示），点单页不要
     */
    public List<DishVO> listDishes(String categoryCode, String tag, boolean includeSoldOut) {
        var query = Wrappers.<Dish>lambdaQuery().eq(Dish::getEnabled, 1);
        if (!includeSoldOut) {
            query.gt(Dish::getStock, 0);
        }

        if (categoryCode != null && !categoryCode.isBlank() && !CODE_SIGNATURE.equals(categoryCode)) {
            Category c = findByCode(categoryCode);
            if (c == null) {
                throw new BizException(ErrorCode.CATEGORY_NOT_FOUND, "分类不存在：" + categoryCode);
            }
            query.eq(Dish::getCategoryId, c.getId());
        }
        query.orderByAsc(Dish::getCategoryId).orderByDesc(Dish::getSold);

        List<Dish> dishes = dishMapper.selectList(query);
        List<DishVO> assembled = assemble(dishes);

        // 标签筛选放在内存里做。菜品只有几十道，为它多写一条 join 不划算；
        // 等规模上去了再改成 EXISTS 子查询
        String wanted = CODE_SIGNATURE.equals(categoryCode) ? DishFeature.TAG_SIGNATURE : tag;
        if (wanted != null && !wanted.isBlank()) {
            assembled = assembled.stream().filter(d -> d.tags().contains(wanted)).toList();
        }
        return assembled;
    }

    public DishVO getByCode(String code) {
        Dish dish = dishMapper.selectOne(Wrappers.<Dish>lambdaQuery().eq(Dish::getCode, code).last("limit 1"));
        if (dish == null) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND, "菜品不存在：" + code);
        }
        return assemble(List.of(dish)).get(0);
    }

    public Dish requireById(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND, "菜品不存在");
        }
        return dish;
    }

    public Category findByCode(String code) {
        return categoryMapper.selectOne(
                Wrappers.<Category>lambdaQuery().eq(Category::getCode, code).last("limit 1"));
    }

    /** 全部在售且有货的菜，推荐接口的候选集 */
    public List<Dish> availableDishes() {
        return dishMapper.selectList(Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getEnabled, 1)
                .gt(Dish::getStock, 0)
                .orderByAsc(Dish::getCategoryId));
    }

    // ------------------------------------------------------------------
    // 组装
    // ------------------------------------------------------------------

    /**
     * 把实体拼成视图。五次查询，与菜品数量无关。
     */
    public List<DishVO> assemble(List<Dish> dishes) {
        if (dishes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = dishes.stream().map(Dish::getId).toList();

        Map<Long, List<String>> tags = groupStrings(
                tagMapper.selectList(Wrappers.<DishTag>lambdaQuery().in(DishTag::getDishId, ids)),
                DishTag::getDishId, DishTag::getTag);

        Map<Long, List<DishIngredient>> ingredients = ingredientMapper
                .selectList(Wrappers.<DishIngredient>lambdaQuery()
                        .in(DishIngredient::getDishId, ids)
                        .orderByAsc(DishIngredient::getSort))
                .stream().collect(Collectors.groupingBy(DishIngredient::getDishId));

        Map<Long, List<DishSpec>> specs = specMapper
                .selectList(Wrappers.<DishSpec>lambdaQuery()
                        .in(DishSpec::getDishId, ids)
                        .orderByAsc(DishSpec::getSort))
                .stream().collect(Collectors.groupingBy(DishSpec::getDishId));

        Set<Long> categoryIds = dishes.stream().map(Dish::getCategoryId).collect(Collectors.toSet());
        Map<Long, Category> categoryById = categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return dishes.stream().map(d -> {
            Category c = categoryById.get(d.getCategoryId());
            List<DishSpec> dishSpecs = specs.getOrDefault(d.getId(), List.of());

            return new DishVO(
                    d.getId(), d.getCode(), d.getName(),
                    c == null ? null : c.getCode(),
                    c == null ? null : c.getName(),
                    d.getPrice(), d.getImg(), d.getBrief(), d.getDetail(),
                    tags.getOrDefault(d.getId(), List.of()),
                    ingredients.getOrDefault(d.getId(), List.of()).stream()
                            .map(DishIngredient::getName).toList(),
                    dishSpecs.stream().map(s -> new DishVO.SpecVO(
                            s.getId(), s.getName(), s.getPriceDelta(),
                            d.getPrice().add(s.getPriceDelta()))).toList(),
                    d.getSpice(), d.getSold(), d.getRating(), d.getPrepMin(),
                    d.getStock(), d.soldOut(), d.getEnabled(), d.getUnit(), d.getSpecLabel());
        }).toList();
    }

    private static <T> Map<Long, List<String>> groupStrings(
            List<T> rows, Function<T, Long> key, Function<T, String> value) {
        Map<Long, List<String>> map = new HashMap<>();
        for (T row : rows) {
            map.computeIfAbsent(key.apply(row), k -> new ArrayList<>()).add(value.apply(row));
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 转成算法输入
    // ------------------------------------------------------------------

    /**
     * 实体 → 算法值对象。
     *
     * <p>这是持久层和算法层之间<b>唯一</b>的转换点。算法包不认识
     * {@code Dish}，也不该认识 —— 它之所以能脱离 Spring 和数据库
     * 单独跑单元测试，就是因为输入是这个手工构造的 {@link DishFeature}。
     *
     * <p>价格这里用的是 {@code BigDecimal.doubleValue()}。虽然
     * {@code double} 存钱有误差，但价格进入算法之后只参与归一化和距离计算，
     * 而这两步对 1e-15 量级的偏差完全不敏感；真正需要精确的地方
     * （下单金额）自始至终走的是 BigDecimal，没有经过这里。
     */
    public List<DishFeature> toFeatures(List<Dish> dishes) {
        if (dishes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = dishes.stream().map(Dish::getId).toList();

        Map<Long, Set<String>> tags = tagMapper
                .selectList(Wrappers.<DishTag>lambdaQuery().in(DishTag::getDishId, ids))
                .stream().collect(Collectors.groupingBy(DishTag::getDishId,
                        Collectors.mapping(DishTag::getTag, Collectors.toSet())));

        Map<Long, List<String>> ingredients = ingredientMapper
                .selectList(Wrappers.<DishIngredient>lambdaQuery().in(DishIngredient::getDishId, ids))
                .stream().collect(Collectors.groupingBy(DishIngredient::getDishId,
                        Collectors.mapping(DishIngredient::getName, Collectors.toList())));

        Set<Long> categoryIds = dishes.stream().map(Dish::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> categoryCodes = new HashMap<>();
        for (Category c : categoryMapper.selectByIds(categoryIds)) {
            categoryCodes.put(c.getId(), c.getCode());
        }

        return dishes.stream().map(d -> new DishFeature(
                d.getId(),
                d.getName(),
                categoryCodes.get(d.getCategoryId()),
                d.getPrice().doubleValue(),
                d.getSpice(),
                tags.getOrDefault(d.getId(), Set.of()),
                d.getSold(),
                d.getRating().doubleValue(),
                d.getPrepMin(),
                ingredients.getOrDefault(d.getId(), List.of())
        )).toList();
    }

    /** 菜品编码 → 实体。下单时按编码校验用 */
    public Map<String, Dish> byCodes(List<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return dishMapper.selectList(Wrappers.<Dish>lambdaQuery().in(Dish::getCode, codes))
                .stream().collect(Collectors.toMap(Dish::getCode, Function.identity(), (a, b) -> a));
    }

    /** 把 DishVO 的规格换算成实际单价，下单时用 */
    public BigDecimal priceOf(Dish dish, Long specId) {
        if (specId == null) {
            return dish.getPrice();
        }
        DishSpec spec = specMapper.selectById(specId);
        if (spec == null || !spec.getDishId().equals(dish.getId())) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND, "规格不存在或不属于这道菜");
        }
        return dish.getPrice().add(spec.getPriceDelta());
    }

    /** 给后台用：按排序取分类，不做计数 */
    public List<Category> allCategories() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort));
    }

    /** 给后台用：全量菜品（含售罄、含下架） */
    public List<Dish> allDishes() {
        return dishMapper.selectList(Wrappers.<Dish>lambdaQuery()
                .orderByAsc(Dish::getCategoryId)
                .orderByDesc(Dish::getSold));
    }

}
