package com.shike.ordering.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shike.ordering.common.BizException;
import com.shike.ordering.common.ErrorCode;
import com.shike.ordering.common.OrderStatus;
import com.shike.ordering.dto.DishVO;
import com.shike.ordering.entity.Dish;
import com.shike.ordering.mapper.DishMapper;
import com.shike.ordering.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台经营看板与菜品维护。
 *
 * <p>这里的统计全部走数据库聚合，而不是把订单全捞到内存里用 Stream 算。
 * 演示期的数据量当然无所谓，但"先把数据取出来再过滤"这个写法一旦定了型，
 * 等真的攒了几个月的订单，后台首页每打开一次就要把整张订单表读进 JVM ——
 * 而这种性能问题通常到上线之后才会暴露。多写一句 SQL 换掉这个隐患是值得的。
 */
@Service
public class AdminService {

    /** 看板的趋势区间。两周足够看出周末和工作日的差别 */
    private static final int TREND_DAYS = 14;

    private final OrderMapper orderMapper;
    private final DishMapper dishMapper;
    private final DishService dishService;

    public AdminService(OrderMapper orderMapper, DishMapper dishMapper, DishService dishService) {
        this.orderMapper = orderMapper;
        this.dishMapper = dishMapper;
        this.dishService = dishService;
    }

    /**
     * 后台首页的全部数据，一次给全。
     *
     * <p>拆成四五个接口让前端并发请求，只会让页面出现"营业额已经变了、
     * 折线还是旧的"这种半新半旧的中间态。看板是一屏信息，
     * 就该是一个快照。
     */
    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime trendFrom = today.minusDays(TREND_DAYS - 1L).atStartOfDay();

        Map<String, Object> out = new LinkedHashMap<>();

        // ---- 今日 ----
        Map<String, Object> todaySummary = orderMapper.summary(dayStart, dayEnd);
        long todayOrders = toLong(todaySummary == null ? null : todaySummary.get("orders"));
        BigDecimal todayAmount = toDecimal(todaySummary == null ? null : todaySummary.get("amount"));

        // ---- 昨日，用来算涨跌 ----
        Map<String, Object> yesterdaySummary =
                orderMapper.summary(dayStart.minusDays(1), dayStart);
        BigDecimal yesterdayAmount = toDecimal(
                yesterdaySummary == null ? null : yesterdaySummary.get("amount"));

        out.put("todayRevenue", todayAmount);
        out.put("todayOrders", todayOrders);
        // 客单价：营业额 ÷ 单数。没有订单时给 0 而不是崩掉 ——
        // 早上刚开店打开后台，一单都没有是常态
        out.put("avgTicket", todayOrders == 0 ? BigDecimal.ZERO
                : todayAmount.divide(BigDecimal.valueOf(todayOrders), 2, RoundingMode.HALF_UP));
        out.put("revenueChange", changeRatio(todayAmount, yesterdayAmount));

        // ---- 14 天趋势。没订单的那天要补 0，否则折线会把空缺的日子直接跳过 ----
        out.put("revenueTrend", fillMissingDays(orderMapper.revenueByDay(trendFrom), today, TREND_DAYS));

        // ---- 分类占比 ----
        out.put("categoryShare", orderMapper.categoryShare(trendFrom));

        // ---- 状态分布 ----
        Map<String, Long> statusCount = new HashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            statusCount.put(s.name(), 0L);
        }
        for (Map<String, Object> row : orderMapper.countByStatus(dayStart.minusDays(TREND_DAYS))) {
            statusCount.put(String.valueOf(row.get("status")), toLong(row.get("cnt")));
        }
        out.put("statusCount", statusCount);

        // ---- 待处理 ----
        out.put("pendingOrders", orderMapper.selectCount(Wrappers.<com.shike.ordering.entity.Order>lambdaQuery()
                .in(com.shike.ordering.entity.Order::getStatus,
                        List.of(OrderStatus.PENDING.name(), OrderStatus.COOKING.name()))));
        out.put("soldOutDishes", dishMapper.selectCount(Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getEnabled, 1).le(Dish::getStock, 0)));

        return out;
    }

    /**
     * 补齐全零的日子。
     *
     * <p>不补的话，10-03 一单没开，折线上 10-02 会直接连到 10-04，
     * 看起来像是那天营业额为零—— 这是对的；但如果是画柱状图，
     * 柱子数量就不对了，日期和数据的对应关系会错位。
     */
    private List<Map<String, Object>> fillMissingDays(List<Map<String, Object>> rows,
                                                      LocalDate today, int days) {
        Map<String, Map<String, Object>> byDay = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byDay.put(String.valueOf(row.get("day")), row);
        }
        List<Map<String, Object>> filled = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String key = d.toString();
            Map<String, Object> row = byDay.get(key);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", key);
            entry.put("label", String.format("%02d-%02d", d.getMonthValue(), d.getDayOfMonth()));
            entry.put("amount", row == null ? BigDecimal.ZERO : toDecimal(row.get("amount")));
            entry.put("orders", row == null ? 0L : toLong(row.get("orders")));
            filled.add(entry);
        }
        return filled;
    }

    /** 环比变化。昨天是 0 时返回 null —— 「增长了百分之几万」没有意义 */
    private Double changeRatio(BigDecimal now, BigDecimal before) {
        if (before == null || before.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return now.subtract(before)
                .divide(before, 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // ------------------------------------------------------------------
    // 菜品维护
    // ------------------------------------------------------------------

    /** 后台的菜品列表，含售罄和已下架的 */
    public List<DishVO> allDishes() {
        return dishService.assemble(dishService.allDishes());
    }

    /**
     * 改价格和库存。
     *
     * <p>只开放这两个字段是有意的：菜名、分类、食材这些一改就会影响
     * 推荐算法的结果和论文里的实验数据。真要改，应该走 data.js → 重跑
     * {@code gen_seed.mjs} 那条路，让两边一起变。
     */
    @Transactional
    public DishVO updateDish(String code, BigDecimal price, Integer stock) {
        Dish dish = dishMapper.selectOne(
                Wrappers.<Dish>lambdaQuery().eq(Dish::getCode, code).last("limit 1"));
        if (dish == null) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND, "菜品不存在：" + code);
        }
        if (price != null) {
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "价格必须大于 0");
            }
            dish.setPrice(price);
        }
        if (stock != null) {
            if (stock < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "库存不能是负数");
            }
            dish.setStock(stock);
        }
        dishMapper.updateById(dish);
        return dishService.getByCode(code);
    }

    /** 上架 / 下架 */
    @Transactional
    public DishVO toggleDish(String code, boolean enabled) {
        Dish dish = dishMapper.selectOne(
                Wrappers.<Dish>lambdaQuery().eq(Dish::getCode, code).last("limit 1"));
        if (dish == null) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND, "菜品不存在：" + code);
        }
        dish.setEnabled(enabled ? 1 : 0);
        dishMapper.updateById(dish);
        return dishService.getByCode(code);
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }
}
