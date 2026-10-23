package com.shike.ordering.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shike.ordering.dto.OrderVO;
import com.shike.ordering.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把订单变化推给后厨屏。
 *
 * <p><b>为什么用 {@code @TransactionalEventListener} 而不是 {@code @EventListener}</b>：
 * 后者在事务提交<b>之前</b>就触发了。万一后面某一步失败回滚，
 * 后厨屏上已经多出一道根本不存在的菜 —— 厨师照着做，做完了发现没这单。
 * {@link TransactionPhase#AFTER_COMMIT} 保证推送只发生在订单真的落库之后。
 *
 * <p>推送的内容是「变了，请重新拉取」而不是完整的订单数据。
 * 这样客户端只要调一次已经存在的 {@code /api/kitchen/orders} 就能拿到
 * 与数据库完全一致的状态，不必在推送里重建一遍业务逻辑 ——
 * 推送里少带一个字段、客户端少解析一次，两边就会不一致，
 * 而"推送的内容和接口返回的内容不一致"是最难排查的一类问题。
 */
@Component
public class KitchenBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(KitchenBroadcaster.class);

    private final KitchenWebSocketHandler handler;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public KitchenBroadcaster(KitchenWebSocketHandler handler, OrderService orderService,
                              ObjectMapper objectMapper) {
        this.handler = handler;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderService.OrderPlacedEvent event) {
        if (handler.onlineCount() == 0) {
            return;
        }
        try {
            List<OrderVO> active = orderService.activeOrders();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "orders-changed");
            payload.put("orderNo", event.orderNo());
            payload.put("online", handler.onlineCount());
            payload.put("orders", active);

            handler.broadcast(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // 推送失败不能影响下单。订单已经落库了，后厨屏刷新一下就能看到
            log.warn("后厨推送失败（不影响下单）：{}", e.getMessage());
        }
    }
}
