package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.common.OrderStatus;
import com.shike.ordering.dto.OrderVO;
import com.shike.ordering.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后厨屏接口。需要 KITCHEN 或 ADMIN 角色。
 *
 * <p>权限在 {@code SecurityConfig} 里按路径统一控制（{@code /api/kitchen/**}），
 * 这里就不再逐个方法挂 {@code @PreAuthorize} 了 ——
 * 两处都写，改了其中一处忘了另一处，就会出现"某个接口意外放行"的漏洞。
 * 鉴权只在一处声明，是能一眼看全的。
 */
@RestController
@RequestMapping("/api/kitchen")
public class KitchenController {

    private final OrderService orderService;

    public KitchenController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 所有进行中的单，最早下的排最前面 —— 后厨要按等待时长做菜 */
    @GetMapping("/orders")
    public ApiResponse<List<OrderVO>> orders() {
        return ApiResponse.ok(orderService.activeOrders());
    }

    /** 后厨屏顶栏：今日已出、待接单数、最长等待 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(orderService.kitchenStats());
    }

    /**
     * 推进订单状态。
     *
     * @param to 目标状态：COOKING 接单开工 / SERVED 出餐上菜 / DONE 完成 / CANCELLED 退单。
     *           非法流转会被状态机挡下来，不会出现"跳过制作直接完成"
     */
    @PostMapping("/orders/{orderNo}/status")
    public ApiResponse<OrderVO> advance(@PathVariable String orderNo,
                                        @RequestParam String to) {
        return ApiResponse.ok(orderService.advance(orderNo, OrderStatus.of(to)));
    }
}
