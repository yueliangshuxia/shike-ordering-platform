package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单。
 *
 * <p>四个金额字段是<b>存下来的</b>而不是每次拿明细现算的：单价会变，
 * 而订单一经成交，它的金额就必须冻结 —— 否则改一次菜单价格，
 * 历史订单的金额就跟着变了，对账和电子小票都站不住。
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    /** 外带时为空 */
    private Long tableId;

    /** PENDING / COOKING / SERVED / DONE / CANCELLED */
    private String status;

    private String payMethod;

    private String remark;

    private BigDecimal subtotal;

    /** 堂食服务费，自取为 0 */
    private BigDecimal serviceFee;

    /** 自提立减，正数表示减掉多少。堂食为 0 */
    private BigDecimal discount;

    private BigDecimal total;

    /** DINE_IN 堂食 / TAKEOUT 自取 */
    private String dineMode;

    /**
     * 下单时间。
     *
     * <p><b>特意不加 {@code insertStrategy = NEVER}</b>：正常下单时它是 null，
     * MyBatis-Plus 默认的 NOT_NULL 策略会把它从 INSERT 里省掉，
     * 数据库的 DEFAULT CURRENT_TIMESTAMP 接管；而灌历史订单时需要指定过去的时间，
     * 加了 NEVER 就永远写不进去，所有演示订单会挤在同一秒。
     */
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
