package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细。
 *
 * <p>{@code dishName} / {@code specName} / {@code unitPrice} 都是<b>下单时的快照</b>，
 * 不是外键回查。菜名和价格以后都会改，但历史订单必须能原样重放出当时那张小票。
 * 后厨打印、订单详情页展示的也一律是这里的快照值。
 */
@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long dishId;

    /** 没有规格时为空 */
    private Long specId;

    private String dishName;

    private String specName;

    /** 含规格加价之后的实际单价 */
    private BigDecimal unitPrice;

    private Integer qty;

    /** 小计 = 单价 × 数量 */
    private BigDecimal amount;
}
