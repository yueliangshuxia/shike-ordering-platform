package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.dto.DishVO;
import com.shike.ordering.dto.OrderVO;
import com.shike.ordering.service.AdminService;
import com.shike.ordering.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 后台管理接口。需要 ADMIN 角色（见 {@code SecurityConfig} 对 {@code /api/admin/**} 的规则）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;

    public AdminController(AdminService adminService, OrderService orderService) {
        this.adminService = adminService;
        this.orderService = orderService;
    }

    /** 经营看板。今日营业额、环比、14 天趋势、分类占比、状态分布一次给全 */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    /** 全部菜品（含售罄、含已下架），供后台列表展示 */
    @GetMapping("/dishes")
    public ApiResponse<List<DishVO>> dishes() {
        return ApiResponse.ok(adminService.allDishes());
    }

    /**
     * 改价格或库存。两个字段都可以不传，传了才改。
     *
     * <p>用 PUT 而不是 PATCH：这个接口的语义就是"把这两个字段设成我给的值"，
     * 幂等，重复调用结果一致。
     */
    @PutMapping("/dishes/{code}")
    public ApiResponse<DishVO> updateDish(@PathVariable String code,
                                          @RequestBody DishUpdate body) {
        return ApiResponse.ok(adminService.updateDish(code, body.price(), body.stock()));
    }

    @PutMapping("/dishes/{code}/enabled")
    public ApiResponse<DishVO> toggleDish(@PathVariable String code,
                                          @RequestBody ToggleUpdate body) {
        return ApiResponse.ok(adminService.toggleDish(code, body.enabled()));
    }

    /** 后厨当下在做的全部订单，后台也能看 */
    @GetMapping("/orders/active")
    public ApiResponse<List<OrderVO>> activeOrders() {
        return ApiResponse.ok(orderService.activeOrders());
    }

    public record DishUpdate(BigDecimal price, Integer stock) {
    }

    public record ToggleUpdate(boolean enabled) {
    }
}
