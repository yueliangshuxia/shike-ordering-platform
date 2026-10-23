package com.shike.ordering.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shike.ordering.algorithm.DishFeature;
import com.shike.ordering.algorithm.UserTaste;
import com.shike.ordering.dto.TasteRequest;
import com.shike.ordering.entity.Category;
import com.shike.ordering.entity.Dish;
import com.shike.ordering.entity.Order;
import com.shike.ordering.entity.OrderItem;
import com.shike.ordering.entity.UserTasteProfile;
import com.shike.ordering.mapper.CategoryMapper;
import com.shike.ordering.mapper.DishMapper;
import com.shike.ordering.mapper.OrderItemMapper;
import com.shike.ordering.mapper.OrderMapper;
import com.shike.ordering.mapper.UserTasteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户口味画像的合成 —— 推荐算法「口味匹配」准则的唯一数据来源。
 *
 * <p>这是论文 3.5 节「显式偏好与隐式行为融合」的落地处，值得一提的是
 * <b>为什么要有这样一次融合</b>：
 *
 * <ul>
 *   <li>只用显式偏好，用户勾错了或者懒得勾，推荐就一直不准；</li>
 *   <li>只用历史订单，新客没有历史，第一次进店给不出任何个性化结果 ——
 *       而"新客也能出结果"恰恰是本方案相对协同过滤的核心优势；
 *   <li>两者融合，新客靠显式偏好立刻可用，老客的历史会逐渐把画像修正得更准。</li>
 * </ul>
 */
@Service
public class TasteService {

    /**
     * 显式勾选的分类保底权重。
     *
     * <p>必须大于 {@code UserTaste} 里的命中阈值 0.5，否则用户明明勾了这个分类，
     * 算法却当没看见。取 0.6 而不是 1.0，是给历史留出余地：
     * 如果用户某个分类点得极多（占比 0.8），历史值会盖过显式值，画像随行为继续漂移。
     */
    private static final double EXPLICIT_FLOOR = 0.6;

    /** 历史订单只回看这么多天。太久以前的偏好参考价值不大，还会稀释近期行为 */
    private static final int HISTORY_DAYS = 90;

    private final UserTasteMapper tasteMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;

    public TasteService(UserTasteMapper tasteMapper, OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper, DishMapper dishMapper,
                        CategoryMapper categoryMapper) {
        this.tasteMapper = tasteMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 算出这个用户在算法眼里的样子。
     *
     * @param userId 未登录传 null，会得到「新客画像」——
     *               各项偏好为空、辣度不设限、无忌口。
     *               这样游客也能拿到推荐，只是不如登录后贴切
     */
    public UserTaste resolve(Long userId) {
        if (userId == null) {
            return UserTaste.firstTimeVisitor();
        }
        UserTasteProfile p = profileOf(userId);

        Map<String, Double> combined = categoryShare(userId);
        if (p != null) {
            for (String code : p.preferCategoryList()) {
                combined.merge(code, EXPLICIT_FLOOR, Math::max);
            }
        }

        return UserTaste.fromHistory(
                combined,
                p == null || p.getMaxSpice() == null ? 3 : p.getMaxSpice(),
                p != null && Integer.valueOf(1).equals(p.getAvoidSpice()),
                p == null ? Set.of() : new HashSet<>(p.avoidIngredientList()));
    }

    /**
     * 按历史订单算各分类的占比。
     *
     * <p>用「订单行数量」而不是「订单次数」来加权：一次点了 6 串烧烤和
     * 点过 6 次烧烤，反映的偏好强度是一样的，都该让烧烤的权重上去。
     * 这也和论文里用销量衡量热度是同一个口径。
     *
     * <p>已取消的订单不计入 —— 那笔没吃成，不构成偏好证据。
     */
    private Map<String, Double> categoryShare(Long userId) {
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .ne(Order::getStatus, "CANCELLED")
                .ge(Order::getCreatedAt, java.time.LocalDateTime.now().minusDays(HISTORY_DAYS)));

        if (orders.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderId, orderIds));
        if (items.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> dishIds = items.stream().map(OrderItem::getDishId).collect(Collectors.toSet());
        Map<Long, Long> categoryOfDish = dishMapper.selectByIds(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Dish::getCategoryId));

        Map<Long, String> codeOfCategory = new HashMap<>();
        for (Category c : categoryMapper.selectByIds(new HashSet<>(categoryOfDish.values()))) {
            codeOfCategory.put(c.getId(), c.getCode());
        }

        Map<String, Long> tally = new HashMap<>();
        long total = 0;
        for (OrderItem item : items) {
            Long catId = categoryOfDish.get(item.getDishId());
            String code = catId == null ? null : codeOfCategory.get(catId);
            if (code == null) {
                continue;
            }
            tally.merge(code, (long) item.getQty(), Long::sum);
            total += item.getQty();
        }
        if (total == 0) {
            return new HashMap<>();
        }

        double denominator = total;
        Map<String, Double> share = new HashMap<>();
        tally.forEach((code, qty) -> share.put(code, qty / denominator));
        return share;
    }

    public UserTasteProfile profileOf(Long userId) {
        return tasteMapper.selectOne(Wrappers.<UserTasteProfile>lambdaQuery()
                .eq(UserTasteProfile::getUserId, userId).last("limit 1"));
    }

    /**
     * 保存口味设置。没有记录就新建，有就更新 —— 前端不需要关心是哪种情况。
     */
    @Transactional
    public void save(Long userId, TasteRequest req) {
        UserTasteProfile p = profileOf(userId);
        if (p == null) {
            p = new UserTasteProfile();
            p.setUserId(userId);
            p.setMaxSpice(3);
            p.setAvoidSpice(0);
        }
        if (req.preferCategories() != null) {
            p.setPreferCategories(join(req.preferCategories()));
        }
        if (req.avoidIngredients() != null) {
            p.setAvoidIngredients(join(req.avoidIngredients()));
        }
        if (req.maxSpice() != null) {
            p.setMaxSpice(Math.max(0, Math.min(3, req.maxSpice())));
        }
        if (req.avoidSpice() != null) {
            p.setAvoidSpice(req.avoidSpice() ? 1 : 0);
        }
        p.setUpdatedAt(java.time.LocalDateTime.now());

        if (p.getId() == null) {
            tasteMapper.insert(p);
        } else {
            tasteMapper.updateById(p);
        }
    }

    /** 去重后拼成逗号串。去重是为了让「勾了两次同一个分类」不会在库里留下重复项 */
    private static String join(List<String> values) {
        return String.join(",", new LinkedHashSet<>(values.stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList()));
    }

    /** 可选的分类清单，给前端渲染偏好勾选框用 */
    public List<String> selectableCategories() {
        List<String> codes = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .eq(Category::getEnabled, 1).orderByAsc(Category::getSort))
                .stream().map(Category::getCode).collect(Collectors.toList());
        codes.add(0, DishFeature.CAT_SIGNATURE);
        return codes;
    }
}
