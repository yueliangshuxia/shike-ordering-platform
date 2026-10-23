package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.dto.CreateOrderRequest;
import com.shike.ordering.dto.OrderVO;
import com.shike.ordering.dto.QuoteVO;
import com.shike.ordering.security.LoginUser;
import com.shike.ordering.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顾客侧的订单接口。<b>全部需要登录</b>。
 *
 * <p>和菜单、推荐不一样，订单必须知道「是谁的」—— 下单要记用户，
 * 查单要防止看到别人的订单。所以这里没有匿名入口。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderVO> create(@AuthenticationPrincipal LoginUser me,
                                       @Valid @RequestBody CreateOrderRequest req) {
        return ApiResponse.ok(orderService.create(me.id(), req));
    }

    /**
     * 金额试算。不落库，也没有副作用。
     *
     * <p>结算页用它把服务费、餐盒费显示出来，而不是自己按「堂食加 5%」
     * 再算一遍 —— 收费规则只应该存在一份。
     *
     * <p>放在 `/api/orders/quote` 而不是 `/api/orders/{no}/...`，
     * 因为路径段必须是复数名词，`{orderNo}` 会和 `quote` 这种字面量撞车。
     */
    @PostMapping("/quote")
    public ApiResponse<QuoteVO> quote(@Valid @RequestBody CreateOrderRequest req) {
        return ApiResponse.ok(orderService.quote(req));
    }

    /** 我的订单，最新的排前面 */
    @GetMapping
    public ApiResponse<List<OrderVO>> mine(@AuthenticationPrincipal LoginUser me) {
        return ApiResponse.ok(orderService.listByUser(me.id()));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderVO> detail(@AuthenticationPrincipal LoginUser me,
                                       @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getByNo(orderNo, me.id(), me.isKitchen()));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderVO> cancel(@AuthenticationPrincipal LoginUser me,
                                       @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.cancel(orderNo, me.id(), me.isKitchen()));
    }

    /**
     * 模拟支付。
     *
     * <p>真实的支付要有网关下单、异步回调、对账这几步，
     * 那是另一个课题。这里只把状态和支付方式落库，
     * 好让订单详情页的展示逻辑是完整的。
     */
    @PostMapping("/{orderNo}/pay")
    public ApiResponse<OrderVO> pay(@AuthenticationPrincipal LoginUser me,
                                    @PathVariable String orderNo,
                                    @RequestParam(required = false) String payMethod) {
        return ApiResponse.ok(orderService.pay(orderNo, me.id(), payMethod));
    }
}
