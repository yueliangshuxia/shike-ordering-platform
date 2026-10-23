package com.shike.ordering.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shike.ordering.entity.DiningTable;
import com.shike.ordering.entity.Dish;
import com.shike.ordering.entity.Order;
import com.shike.ordering.entity.OrderItem;
import com.shike.ordering.entity.User;
import com.shike.ordering.entity.UserTasteProfile;
import com.shike.ordering.mapper.DiningTableMapper;
import com.shike.ordering.mapper.DishMapper;
import com.shike.ordering.mapper.OrderItemMapper;
import com.shike.ordering.mapper.OrderMapper;
import com.shike.ordering.mapper.UserMapper;
import com.shike.ordering.mapper.UserTasteMapper;
import com.shike.ordering.service.FeeCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 首次启动时补齐演示数据。
 *
 * <p>账号和订单<b>不放进 data.sql</b>：密码要用 BCrypt 现算，
 * 写死在 SQL 里等于把摘要固化下来，将来调密码强度或换编码器就得重来一遍。
 * 交给这里在启动时算，既干净又保证每次都是当前编码器的产物。
 *
 * <p><b>幂等</b>：所有步骤都先查有没有再插，所以重启多少次都不会重复。
 * 也正因如此，管理员在后台改过的数据不会被启动流程覆盖回去。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 演示账号。密码是明文写在代码里的 —— 这是演示库，不是生产库，写清楚比藏着好 */
    private static final String CUSTOMER_USERNAME = "13800000621";
    private static final String CUSTOMER_PASSWORD = "123456";
    private static final String ADMIN_USERNAME = "13900000001";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String KITCHEN_USERNAME = "13900000002";
    private static final String KITCHEN_PASSWORD = "kitchen123";

    private final UserMapper userMapper;
    private final UserTasteMapper tasteMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;
    private final DiningTableMapper tableMapper;
    private final PasswordEncoder passwordEncoder;
    private final FeeCalculator feeCalculator;

    public DataInitializer(UserMapper userMapper, UserTasteMapper tasteMapper,
                           OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                           DishMapper dishMapper, DiningTableMapper tableMapper,
                           PasswordEncoder passwordEncoder, FeeCalculator feeCalculator) {
        this.userMapper = userMapper;
        this.tasteMapper = tasteMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.dishMapper = dishMapper;
        this.tableMapper = tableMapper;
        this.passwordEncoder = passwordEncoder;
        this.feeCalculator = feeCalculator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User customer = ensureUser(CUSTOMER_USERNAME, CUSTOMER_PASSWORD, "陈同学", "CUSTOMER", "常客");
        ensureUser(ADMIN_USERNAME, ADMIN_PASSWORD, "店长", "ADMIN", "熟客");
        ensureUser(KITCHEN_USERNAME, KITCHEN_PASSWORD, "后厨屏", "KITCHEN", "熟客");

        ensureTasteProfile(customer.getId());
        ensureHistory(customer.getId());

        log.info("演示数据就绪。顾客 {} / {}，管理员 {} / {}，后厨 {} / {}",
                CUSTOMER_USERNAME, CUSTOMER_PASSWORD,
                ADMIN_USERNAME, ADMIN_PASSWORD,
                KITCHEN_USERNAME, KITCHEN_PASSWORD);
    }

    private User ensureUser(String username, String rawPassword, String nickname,
                            String role, String level) {
        User existing = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setNickname(nickname);
        u.setPhone(username);
        u.setRole(role);
        u.setLevel(level);
        userMapper.insert(u);
        return u;
    }

    /**
     * 演示顾客的口味画像。
     *
     * <p>偏好「点心蒸点 + 粥粉面」、辣度上限 2、忌口内脏 —— 这几项是特意选的，
     * 好让推荐页能演示出效果：点心那几道会被抬上来；
     * 麻婆豆腐是辣的（辣度 2），刚好卡在上限上不会被压；
     * 而忌口内脏这一项在有肝/肚/肠的菜上会立刻显出来。
     */
    private void ensureTasteProfile(Long userId) {
        Long count = tasteMapper.selectCount(
                Wrappers.<UserTasteProfile>lambdaQuery().eq(UserTasteProfile::getUserId, userId));
        if (count > 0) {
            return;
        }
        UserTasteProfile p = new UserTasteProfile();
        p.setUserId(userId);
        p.setPreferCategories("dimsum,congee");
        p.setMaxSpice(2);
        p.setAvoidSpice(0);
        p.setAvoidIngredients("内脏");
        tasteMapper.insert(p);
    }

    /**
     * 造一批历史订单。
     *
     * <p>不是为了好看 —— {@code UserTaste.fromHistory} 要靠分类占比算隐式偏好，
     * 一个没有任何历史的新账号，推荐结果和游客完全一样，
     * 「历史订单数据」这个题目里的关键词就演示不出来了。
     * 所以这里刻意让烧烤和蒸点占大头，和显式偏好对得上。
     */
    private void ensureHistory(Long userId) {
        Long count = orderMapper.selectCount(
                Wrappers.<Order>lambdaQuery().eq(Order::getUserId, userId));
        if (count > 0) {
            return;
        }
        List<DiningTable> tables = tableMapper.selectList(null);
        if (tables.isEmpty()) {
            log.warn("t_dining_table 是空的，跳过历史订单生成");
            return;
        }

        // 每单的菜，按菜品编码写。找不到的编码会被跳过，不会因此启动失败。
        //
        // 刻意让点心和粥粉面占大头 —— 演示账号的口味画像里
        // preferCats 就是「招牌推荐 + 点心蒸点 + 粥粉面」，
        // 历史订单和显式偏好互相印证，演示「隐式行为 + 显式偏好融合」时才说得通。
        List<String[]> history = List.of(
                new String[]{"D1001", "2", "D2001", "1", "D5001", "2"},
                new String[]{"D1002", "1", "D2004", "1", "D3001", "1", "D4001", "1"},
                new String[]{"D1004", "3", "D2005", "1", "D5002", "2"},
                new String[]{"D1001", "1", "D1003", "1", "D2003", "2"},
                new String[]{"D1005", "1", "D5003", "1"},
                new String[]{"D1004", "2", "D2001", "2", "D4002", "1"},
                new String[]{"D1003", "2", "D2002", "1", "D5001", "1"});

        int seq = 1;
        for (int i = 0; i < history.size(); i++) {
            String[] flat = history.get(i);
            // 越靠前的越早，跨度铺满近 30 天，这样"近 30 天销量"的口径说得通
            LocalDateTime when = LocalDateTime.now().minusDays((long) (history.size() - i) * 4).withHour(12);

            Order order = new Order();
            order.setOrderNo("SK" + when.format(ORDER_NO_DATE) + String.format("%03d", seq++));
            order.setUserId(userId);
            order.setTableId(tables.get(i % tables.size()).getId());
            order.setStatus("DONE");
            order.setPayMethod(i % 2 == 0 ? "微信支付" : "支付宝");
            order.setRemark("");
            order.setDineMode(FeeCalculator.DINE_IN);
            order.setCreatedAt(when);
            order.setFinishedAt(when.plusMinutes(45));

            BigDecimal subtotal = BigDecimal.ZERO;
            orderMapper.insert(order);

            for (int k = 0; k + 1 < flat.length; k += 2) {
                Dish dish = dishMapper.selectOne(Wrappers.<Dish>lambdaQuery()
                        .eq(Dish::getCode, flat[k]).last("limit 1"));
                if (dish == null) {
                    continue;
                }
                int qty = Integer.parseInt(flat[k + 1]);
                BigDecimal amount = dish.getPrice().multiply(BigDecimal.valueOf(qty));

                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setDishId(dish.getId());
                item.setDishName(dish.getName());
                item.setUnitPrice(dish.getPrice());
                item.setQty(qty);
                item.setAmount(amount);
                orderItemMapper.insert(item);

                subtotal = subtotal.add(amount);
            }

            var fees = feeCalculator.compute(subtotal, FeeCalculator.DINE_IN);
            order.setSubtotal(fees.subtotal());
            order.setServiceFee(fees.serviceFee());
            order.setDiscount(fees.discount());
            order.setTotal(fees.total());
            orderMapper.updateById(order);
        }
        log.info("已生成 {} 笔历史订单", history.size());
    }
}
