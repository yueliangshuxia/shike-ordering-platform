package com.shike.ordering.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 收费口径。
 *
 * <p>单独抽出来是因为这几个数会变，而且改起来应该有明确的一处。
 * 早先它们散在 Service 里当魔法数字，还因此出过「结算页和订单详情两个价」的事故。
 *
 * <p>2026-10-17 用户拍板：**不收服务费**。比例配成 0 而不是把机制删掉 ——
 * 哪天真要收，改这一行就行，不用回头动 FeeCalculator。见 `设计确认清单.md` 第 2 条。
 */
@Data
@Component
@ConfigurationProperties(prefix = "shike.order")
public class OrderProperties {

    /** 服务费比例。当前为 0（不收），机制保留 */
    private BigDecimal serviceFeeRate = new BigDecimal("0.00");

    /** 自提立减比例。只对自取生效 */
    private BigDecimal takeoutDiscountRate = new BigDecimal("0.02");

    /** 自提立减的上限（元）。小额订单按比例减，大额订单封顶 */
    private BigDecimal takeoutDiscountCap = new BigDecimal("4.00");
}
