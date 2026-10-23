package com.shike.ordering.service;

import com.shike.ordering.config.OrderProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 订单金额的唯一算法。
 *
 * <p><b>必须是唯一的一处。</b>演示版就栽在这上面：结算页按「堂食加 5%」算，
 * 订单详情页却对已经含服务费的合计又乘了一次 1.05，同一个订单两个价。
 * 所以现在金额只在这里算，结算接口算完存进订单表，
 * 订单详情直接读存下来的值，不再重算。
 *
 * <h3>当前收费口径</h3>
 * <ul>
 *   <li><b>不收服务费</b>。国内中式小馆收服务费不常见，用户 2026-10-17 明确取消。
 *       机制留着（{@code shike.order.service-fee-rate} 配成 0），
 *       哪天真要收，改一行配置就行。</li>
 *   <li><b>自提立减</b>：自取的订单减掉小计的 2%，最多减 4 元。
 *       这是国内餐饮平台通行的做法 —— 顾客自己来拿，省了服务员跑一趟和打包的工夫，
 *       让一点利给顾客，比"收打包费"更好接受，也更符合堂食小馆的实际情况。</li>
 * </ul>
 *
 * <p>两项都是加价/减免而不是另立名目，所以 {@code total = 小计 + 服务费 − 立减}
 * 这一个式子对堂食和自取都成立，不需要按就餐方式分支去算总额。
 */
@Service
public class FeeCalculator {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    /** 堂食与自取的分界值。写在一处，免得各处拼字符串拼错 */
    public static final String DINE_IN = "DINE_IN";
    public static final String TAKEOUT = "TAKEOUT";

    private final OrderProperties props;

    public FeeCalculator(OrderProperties props) {
        this.props = props;
    }

    /**
     * @param subtotal 菜品小计
     * @param dineMode {@link #DINE_IN} 或 {@link #TAKEOUT}
     */
    public Fees compute(BigDecimal subtotal, String dineMode) {
        BigDecimal sub = subtotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal service = sub.multiply(props.getServiceFeeRate()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = ZERO;
        if (TAKEOUT.equals(dineMode)) {
            discount = sub.multiply(props.getTakeoutDiscountRate())
                    .min(props.getTakeoutDiscountCap())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = sub.add(service).subtract(discount);
        // 减免理论上不会超过小计（有上限管着），但真出现异常配置时
        // 宁可能把总额算成负数，也不能让订单金额是负的
        if (total.compareTo(ZERO) < 0) {
            total = ZERO;
        }
        return new Fees(sub, service, discount, total);
    }

    /**
     * 一笔订单的金额明细。
     *
     * <p>{@code total} 是算出来的而不是传进来的 —— 让调用方自己加总和
     * 让调用方自己传一个可能对不上的 total，是同一类错误的两种写法。
     *
     * @param discount 自提立减，<b>正数</b>表示减掉多少。堂食为 0
     */
    public record Fees(BigDecimal subtotal, BigDecimal serviceFee,
                       BigDecimal discount, BigDecimal total) {
    }
}
