package com.shike.ordering.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图。
 *
 * <p>四个金额字段是<b>从订单表读出来的</b>，不是拿明细现算的。
 * 演示版正是栽在这里：结算页按堂食算了一次服务费，订单详情页
 * 又对已经含服务费的合计乘了一次 1.05，同一个订单两个价。
 * 现在金额只在 {@code FeeCalculator} 里算一次、存下来，之后只读不算。
 *
 * @param statusLabel 状态的中文说法。放服务端是为了让「PENDING → 待接单」
 *                    这个映射只有一份，前端不必各写各的 switch
 * @param cancellable 现在还能不能取消。判断放在服务端，
 *                    前端只管按这个布尔值决定按钮亮不亮
 */
public record OrderVO(
        Long id,
        String orderNo,
        String status,
        String statusLabel,
        boolean cancellable,
        String dineMode,
        String tableCode,
        String payMethod,
        String remark,
        BigDecimal subtotal,
        BigDecimal serviceFee,
        BigDecimal discount,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        List<ItemVO> items
) {

    /**
     * 订单行。
     *
     * <p>{@code dishName} / {@code specName} / {@code unitPrice} 都是
     * <b>下单那一刻的快照</b>。菜名和价格以后会改，但历史订单必须
     * 原样重放得出当时那张小票 —— 这也是后厨打印和订单详情展示的值。
     */
    public record ItemVO(
            Long dishId,
            String dishCode,
            String dishName,
            String specName,
            BigDecimal unitPrice,
            Integer qty,
            BigDecimal amount,
            String img
    ) {
    }
}
