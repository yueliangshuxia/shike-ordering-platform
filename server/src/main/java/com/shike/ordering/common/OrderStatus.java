package com.shike.ordering.common;

import java.util.Set;

/**
 * 订单状态机。
 *
 * <pre>
 *   PENDING 待接单 ──► COOKING 制作中 ──► SERVED 已上菜 ──► DONE 已完成
 *        │                  │                 │
 *        └──────────────────┴─────────────────┴──────► CANCELLED 已取消
 * </pre>
 *
 * <p>把「下一步能去哪」写进枚举而不是散在各处的 if，是因为这种状态流转
 * 一旦有两处判断不一致，就会出现「后厨点了一下，订单跳过了上菜直接完成」
 * 这类很难复现的问题。
 *
 * <p>终态只有 DONE 和 CANCELLED 两个：到了终态就不再接受任何流转，
 * 也不再允许取消。
 */
public enum OrderStatus {

    PENDING("待接单"),
    COOKING("制作中"),
    SERVED("已上菜"),
    DONE("已完成"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** CANCELLED 之前的这些状态算「进行中」，后厨屏和库存占用都看它 */
    public boolean active() {
        return this == PENDING || this == COOKING || this == SERVED;
    }

    public boolean terminal() {
        return this == DONE || this == CANCELLED;
    }

    /** 后厨点「下一步」时允许去哪。做成集合是为了将来插入新的中间态不用改逻辑 */
    public Set<OrderStatus> nextStates() {
        return switch (this) {
            case PENDING -> Set.of(COOKING, CANCELLED);
            case COOKING -> Set.of(SERVED, CANCELLED);
            case SERVED -> Set.of(DONE, CANCELLED);
            case DONE, CANCELLED -> Set.of();
        };
    }

    public static OrderStatus of(String raw) {
        if (raw == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单状态不能为空");
        }
        for (OrderStatus s : values()) {
            if (s.name().equalsIgnoreCase(raw)) {
                return s;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的订单状态：" + raw);
    }
}
