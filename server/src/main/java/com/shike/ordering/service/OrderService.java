package com.shike.ordering.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shike.ordering.common.BizException;
import com.shike.ordering.common.ErrorCode;
import com.shike.ordering.common.OrderStatus;
import com.shike.ordering.dto.CreateOrderRequest;
import com.shike.ordering.dto.OrderVO;
import com.shike.ordering.dto.QuoteVO;
import com.shike.ordering.entity.DiningTable;
import com.shike.ordering.entity.Dish;
import com.shike.ordering.entity.DishSpec;
import com.shike.ordering.entity.Order;
import com.shike.ordering.entity.OrderItem;
import com.shike.ordering.mapper.DiningTableMapper;
import com.shike.ordering.mapper.DishMapper;
import com.shike.ordering.mapper.DishSpecMapper;
import com.shike.ordering.mapper.OrderItemMapper;
import com.shike.ordering.mapper.OrderMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 下单、查单、取消、后厨流转。
 *
 * <p>这个类里最重要的是两个「不能想当然」的地方：
 *
 * <ol>
 *   <li><b>金额只在服务端算。</b>请求里根本没有价格字段，单价一律从库里取。
 *       否则改一下请求体就能一分钱买走一桌菜。</li>
 *   <li><b>扣库存靠一条带条件的 UPDATE。</b>先查后改在两人抢最后一份时
 *       会双双通过检查，库存变成负数。详见 {@code DishMapper.deductStock}。</li>
 * </ol>
 */
@Service
public class OrderService {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;
    private final DishSpecMapper specMapper;
    private final DiningTableMapper tableMapper;
    private final FeeCalculator feeCalculator;
    private final UserService userService;
    private final ApplicationEventPublisher events;

    public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                        DishMapper dishMapper, DishSpecMapper specMapper,
                        DiningTableMapper tableMapper, FeeCalculator feeCalculator,
                        UserService userService, ApplicationEventPublisher events) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.dishMapper = dishMapper;
        this.specMapper = specMapper;
        this.tableMapper = tableMapper;
        this.feeCalculator = feeCalculator;
        this.userService = userService;
        this.events = events;
    }

    // ------------------------------------------------------------------
    // 下单
    // ------------------------------------------------------------------

    /**
     * 试算金额，不落库。
     *
     * <p>结算页要在用户点「提交订单」<b>之前</b>就把服务费和餐盒费显示出来。
     * 让前端自己按"堂食加 5%"算一遍是最省事的写法，但那正是演示版出过的错：
     * 前端算一遍、后端算一遍，两边的规则一漂，页面报的价和订单实付就对不上，
     * 而且只在改了收费口径之后才暴露。
     *
     * <p>所以收费规则只在 {@code FeeCalculator} 里存在一份，
     * 前端通过这个接口拿结果。副作用为零，可以随便调。
     */
    public QuoteVO quote(CreateOrderRequest req) {
        String dineMode = normalizeDineMode(req.dineMode());
        Map<String, Dish> dishByCode = loadDishes(req);

        List<QuoteVO.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CreateOrderRequest.Item line : req.items()) {
            Dish dish = dishByCode.get(line.dishCode());
            if (dish == null || dish.soldOut()) {
                continue;
            }
            BigDecimal unit = priceWith(dish, line.specId());
            DishSpec spec = line.specId() == null ? null : specMapper.selectById(line.specId());
            BigDecimal amount = unit.multiply(BigDecimal.valueOf(line.qty()))
                    .setScale(2, RoundingMode.HALF_UP);

            lines.add(new QuoteVO.Line(dish.getCode(), dish.getName(),
                    spec == null ? null : spec.getName(), unit, line.qty(), amount));
            subtotal = subtotal.add(amount);
        }

        FeeCalculator.Fees fees = feeCalculator.compute(subtotal, dineMode);
        return new QuoteVO(dineMode, List.copyOf(lines), fees.subtotal(), fees.serviceFee(),
                fees.discount(), fees.total());
    }


    @Transactional
    public OrderVO create(Long userId, CreateOrderRequest req) {
        String dineMode = normalizeDineMode(req.dineMode());

        Map<String, Dish> dishByCode = loadDishes(req);

        // 先把整单要的量汇总，再逐道扣库存。
        // 同一道菜选了两种规格会在请求里出现两行，必须合并后再扣 ——
        // 分开扣的话，两个 3 份各自都能通过 stock >= 3 的检查，
        // 而实际需要 6 份，库存照样会被扣穿
        Map<Long, Integer> qtyByDish = new HashMap<>();
        List<OrderItem> pending = new ArrayList<>();

        for (CreateOrderRequest.Item line : req.items()) {
            Dish dish = dishByCode.get(line.dishCode());
            if (dish == null) {
                throw new BizException(ErrorCode.DISH_NOT_FOUND, "菜品不存在：" + line.dishCode());
            }
            if (dish.soldOut()) {
                throw new BizException(ErrorCode.DISH_SOLD_OUT, dish.getName() + " 已经卖完了");
            }

            BigDecimal unitPrice = priceWith(dish, line.specId());
            DishSpec spec = line.specId() == null ? null : specMapper.selectById(line.specId());

            OrderItem item = new OrderItem();
            item.setDishId(dish.getId());
            item.setSpecId(line.specId());
            // 菜名和规格名存快照：以后改了菜单，历史小票还得还原得出来
            item.setDishName(dish.getName());
            item.setSpecName(spec == null ? null : spec.getName());
            item.setUnitPrice(unitPrice);
            item.setQty(line.qty());
            item.setAmount(unitPrice.multiply(BigDecimal.valueOf(line.qty()))
                    .setScale(2, RoundingMode.HALF_UP));
            pending.add(item);

            qtyByDish.merge(dish.getId(), line.qty(), Integer::sum);
        }

        for (Map.Entry<Long, Integer> e : qtyByDish.entrySet()) {
            if (dishMapper.deductStock(e.getKey(), e.getValue()) == 0) {
                Dish d = dishMapper.selectById(e.getKey());
                String name = d == null ? "该菜品" : d.getName();
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                        name + " 的库存不足，请减少数量或换一道");
            }
        }

        BigDecimal subtotal = pending.stream()
                .map(OrderItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        FeeCalculator.Fees fees = feeCalculator.compute(subtotal, dineMode);

        Order order = new Order();
        order.setOrderNo(nextOrderNo());
        order.setUserId(userId);
        order.setTableId(resolveTableId(req.tableCode()));
        order.setStatus(OrderStatus.PENDING.name());
        order.setPayMethod(req.payMethod() == null || req.payMethod().isBlank() ? "未支付" : req.payMethod());
        order.setRemark(req.remark());
        order.setDineMode(dineMode);
        order.setSubtotal(fees.subtotal());
        order.setServiceFee(fees.serviceFee());
        order.setDiscount(fees.discount());
        order.setTotal(fees.total());
        orderMapper.insert(order);

        for (OrderItem item : pending) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        userService.refreshLevel(userId);

        // 后厨推送交给事务提交后再发：回滚掉的订单不该出现在后厨屏上，
        // 否则厨师会去做一道根本不存在的菜
        events.publishEvent(new OrderPlacedEvent(order.getId(), order.getOrderNo()));

        return toVO(order, pending, req.tableCode());
    }

    private Map<String, Dish> loadDishes(CreateOrderRequest req) {
        List<String> codes = req.items().stream()
                .map(CreateOrderRequest.Item::dishCode)
                .distinct()
                .toList();
        return dishMapper.selectList(Wrappers.<Dish>lambdaQuery().in(Dish::getCode, codes))
                .stream()
                .collect(Collectors.toMap(Dish::getCode, Function.identity(), (a, b) -> a));
    }

    private BigDecimal priceWith(Dish dish, Long specId) {
        if (specId == null) {
            return dish.getPrice();
        }
        DishSpec spec = specMapper.selectById(specId);
        if (spec == null || !spec.getDishId().equals(dish.getId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "规格不属于这道菜：" + dish.getName());
        }
        return dish.getPrice().add(spec.getPriceDelta());
    }

    private String normalizeDineMode(String raw) {
        if (FeeCalculator.TAKEOUT.equalsIgnoreCase(raw)) {
            return FeeCalculator.TAKEOUT;
        }
        return FeeCalculator.DINE_IN;
    }

    private Long resolveTableId(String tableCode) {
        if (tableCode == null || tableCode.isBlank()) {
            return null;
        }
        DiningTable t = tableMapper.selectOne(Wrappers.<DiningTable>lambdaQuery()
                .eq(DiningTable::getCode, tableCode).last("limit 1"));
        if (t == null) {
            throw new BizException(ErrorCode.TABLE_NOT_FOUND, "桌号不存在：" + tableCode);
        }
        return t.getId();
    }

    /**
     * 生成订单号：SK + 年月日时分秒 + 两位随机数。
     *
     * <p>没有用「当天第几单」这种更好读的编法，是因为它需要一次
     * 「查最大值再加一」，而那一步在并发下会撞车：两个人同时下单拿到同一个号，
     * 数据库的唯一索引会把后一个打回去，用户看到的是莫名其妙的失败。
     * 要真正解决得靠数据库序列或者 Redis 自增，而本项目连 Redis 都没装。
     *
     * <p>时间戳 + 随机数在单店场景下够用（撞号需要同一秒且随机数也相同），
     * 而且天然按时间有序。唯一索引仍然保留，作为最后一道保险。
     */
    private String nextOrderNo() {
        return "SK" + LocalDateTime.now().format(NO_FORMAT)
                + String.format("%02d", ThreadLocalRandom.current().nextInt(100));
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public List<OrderVO> listByUser(Long userId) {
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt));
        return assemble(orders);
    }

    public OrderVO getByNo(String orderNo, Long userId, boolean staff) {
        Order order = requireByNo(orderNo);
        // 非店员只能看自己的单。少了这一层，改一下 URL 里的订单号
        // 就能看到别人的订单和手机号
        if (!staff && !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "这不是你的订单");
        }
        return assemble(List.of(order)).get(0);
    }

    public Order requireByNo(String orderNo) {
        Order order = orderMapper.selectOne(
                Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo).last("limit 1"));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在：" + orderNo);
        }
        return order;
    }

    /** 后厨屏用：所有进行中的单，最久的排最前面 */
    public List<OrderVO> activeOrders() {
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .in(Order::getStatus, List.of(OrderStatus.PENDING.name(),
                        OrderStatus.COOKING.name(), OrderStatus.SERVED.name()))
                .orderByAsc(Order::getCreatedAt));
        return assemble(orders);
    }

    /**
     * 后厨屏顶栏的几个数。
     *
     * <p>「今日已出」是<b>从订单表数出来的</b>，不是一个写死的基数。
     * 演示版把它写成了常量 142，结果那张屏出多少单它都不变 ——
     * 一个永远不动的数字比没有数字更让人困惑。
     *
     * <p>「最长等待」只统计还没出完的单。演示版算的是整个队列，
     * 单子全出完了下面还挂着「最长等待 17 分」，自相矛盾。
     */
    public Map<String, Object> kitchenStats() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();

        long doneToday = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.DONE.name())
                .ge(Order::getCreatedAt, dayStart));

        long pending = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.PENDING.name()));

        List<Order> live = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .in(Order::getStatus, List.of(OrderStatus.PENDING.name(),
                        OrderStatus.COOKING.name(), OrderStatus.SERVED.name())));

        long maxWait = live.stream()
                .mapToLong(o -> Duration.between(o.getCreatedAt(), LocalDateTime.now()).toMinutes())
                .max()
                .orElse(0L);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("doneToday", doneToday);
        out.put("pendingCount", pending);
        out.put("activeCount", live.size());
        out.put("maxWaitMinutes", Math.max(0, maxWait));
        return out;
    }

    private List<OrderVO> assemble(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = orderItemMapper
                .selectList(Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));

        Map<Long, String> tableCodeById = new HashMap<>();
        List<Long> tableIds = orders.stream().map(Order::getTableId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!tableIds.isEmpty()) {
            for (DiningTable t : tableMapper.selectByIds(tableIds)) {
                tableCodeById.put(t.getId(), t.getCode());
            }
        }

        // 菜品图片不在订单里存快照（图换了不影响历史小票的金额，
        // 但列表上有个图好看很多），所以按需补一次
        Map<Long, String> imgByDishId = new HashMap<>();
        List<Long> dishIds = itemsByOrder.values().stream()
                .flatMap(List::stream).map(OrderItem::getDishId).distinct().toList();
        if (!dishIds.isEmpty()) {
            for (Dish d : dishMapper.selectByIds(dishIds)) {
                imgByDishId.put(d.getId(), d.getImg());
            }
        }

        return orders.stream().map(o -> toVO(
                o,
                itemsByOrder.getOrDefault(o.getId(), List.of()),
                o.getTableId() == null ? null : tableCodeById.get(o.getTableId()),
                imgByDishId)).toList();
    }

    private OrderVO toVO(Order o, List<OrderItem> items, String tableCode) {
        return toVO(o, items, tableCode, Map.of());
    }

    private OrderVO toVO(Order o, List<OrderItem> items, String tableCode, Map<Long, String> imgByDishId) {
        OrderStatus status = OrderStatus.of(o.getStatus());
        List<OrderVO.ItemVO> itemVOs = items.stream().map(i -> new OrderVO.ItemVO(
                i.getDishId(), null, i.getDishName(), i.getSpecName(),
                i.getUnitPrice(), i.getQty(), i.getAmount(),
                imgByDishId.get(i.getDishId()))).toList();

        return new OrderVO(
                o.getId(), o.getOrderNo(), status.name(), status.label(),
                status.nextStates().contains(OrderStatus.CANCELLED),
                o.getDineMode(), tableCode, o.getPayMethod(), o.getRemark(),
                o.getSubtotal(), o.getServiceFee(), o.getDiscount(), o.getTotal(),
                o.getCreatedAt(), o.getFinishedAt(), itemVOs);
    }

    // ------------------------------------------------------------------
    // 状态流转
    // ------------------------------------------------------------------

    @Transactional
    public OrderVO cancel(String orderNo, Long userId, boolean staff) {
        Order order = requireByNo(orderNo);
        if (!staff && !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "这不是你的订单");
        }

        OrderStatus current = OrderStatus.of(order.getStatus());
        if (!current.nextStates().contains(OrderStatus.CANCELLED)) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "订单已经是「" + current.label() + "」，不能再取消了");
        }

        // 取消要把库存还回去，否则菜会凭空消失 ——
        // 下单扣了库存，取消不退，几次之后菜单上就全是"已售罄"了
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, order.getId()));
        Map<Long, Integer> restore = items.stream().collect(Collectors.toMap(
                OrderItem::getDishId, OrderItem::getQty, Integer::sum));
        restore.forEach(dishMapper::restoreStock);

        order.setStatus(OrderStatus.CANCELLED.name());
        order.setFinishedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        events.publishEvent(new OrderPlacedEvent(order.getId(), order.getOrderNo()));
        return assemble(List.of(order)).get(0);
    }

    /**
     * 后厨推进状态。只能顺着状态机走，不能跳。
     */
    @Transactional
    public OrderVO advance(String orderNo, OrderStatus target) {
        Order order = requireByNo(orderNo);
        OrderStatus current = OrderStatus.of(order.getStatus());

        if (!current.nextStates().contains(target)) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "不能从「" + current.label() + "」直接变成「" + target.label() + "」");
        }

        order.setStatus(target.name());
        if (target.terminal()) {
            order.setFinishedAt(LocalDateTime.now());
            // 上菜之后桌子腾出来了，后台的翻台率才说得准
            if (order.getTableId() != null && target == OrderStatus.DONE) {
                tableMapper.update(null, Wrappers.<DiningTable>lambdaUpdate()
                        .eq(DiningTable::getId, order.getTableId())
                        .set(DiningTable::getStatus, "FREE"));
            }
        } else if (order.getTableId() != null) {
            tableMapper.update(null, Wrappers.<DiningTable>lambdaUpdate()
                    .eq(DiningTable::getId, order.getTableId())
                    .set(DiningTable::getStatus, "OCCUPIED"));
        }
        orderMapper.updateById(order);

        events.publishEvent(new OrderPlacedEvent(order.getId(), order.getOrderNo()));
        return assemble(List.of(order)).get(0);
    }

    /** 模拟支付。真实系统这里要对接支付网关并处理异步回调 */
    @Transactional
    public OrderVO pay(String orderNo, Long userId, String payMethod) {
        Order order = requireByNo(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "这不是你的订单");
        }
        if (OrderStatus.of(order.getStatus()).terminal()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "订单已经结束了，不用再支付");
        }
        order.setPayMethod(payMethod == null || payMethod.isBlank() ? "微信支付" : payMethod);
        orderMapper.updateById(order);
        return assemble(List.of(order)).get(0);
    }

    /** 下单/流转之后广播给后厨屏。用事件而不是直接调 WebSocket，见 create 里的说明 */
    public record OrderPlacedEvent(Long orderId, String orderNo) {
    }
}
