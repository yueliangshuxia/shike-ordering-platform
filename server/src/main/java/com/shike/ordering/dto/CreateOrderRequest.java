package com.shike.ordering.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 下单请求。
 *
 * <p><b>前端传的是菜品编码和数量，不传价格。</b>这不是疏忽 ——
 * 如果前端把单价传上来、后端照着算，那么任何人改一下请求体就能
 * 一分钱买走整桌菜。价格必须以服务端数据库里那一份为准，
 * 所以这里压根没有价格字段可传。
 */
public record CreateOrderRequest(

        /** 桌号，如 A12。自取时可以不传 */
        @Size(max = 16, message = "桌号过长")
        String tableCode,

        /** DINE_IN 堂食 / TAKEOUT 自取 */
        @NotBlank(message = "请选择就餐方式")
        String dineMode,

        @Size(max = 16, message = "支付方式过长")
        String payMethod,

        @Size(max = 255, message = "备注过长")
        String remark,

        @NotEmpty(message = "购物车是空的")
        @Valid
        List<Item> items
) {

    public record Item(
            @NotBlank(message = "菜品编码不能为空")
            String dishCode,

            /** 规格 id。没有规格的菜传 null */
            Long specId,

            @NotNull(message = "数量不能为空")
            @Min(value = 1, message = "数量至少为 1")
            Integer qty
    ) {
    }
}
