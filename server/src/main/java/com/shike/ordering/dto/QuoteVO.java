package com.shike.ordering.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 结算页的金额试算结果。不落库，只是一个报价。
 *
 * <p>把每一行也带回来，是为了让结算页能显示「哪道菜被算了几份、单价多少」——
 * 只回一个总数的话，用户发现金额不对时无从核对。
 *
 * @param dineMode   实际生效的就餐方式。用户传了别的前端也不用猜，
 *                   以这个字段为准（服务端不认的值一律当堂食）
 * @param serviceFee 服务费。当前不收，恒为 0
 * @param discount   自提立减，<b>正数</b>表示减掉多少。堂食为 0
 */
public record QuoteVO(
        String dineMode,
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal serviceFee,
        BigDecimal discount,
        BigDecimal total
) {

    /**
     * 报价里的一行。
     *
     * @param specName 没有规格时为 null
     * @param unitPrice 已含规格加价的单价
     */
    public record Line(
            String dishCode,
            String dishName,
            String specName,
            BigDecimal unitPrice,
            Integer qty,
            BigDecimal amount
    ) {
    }
}
