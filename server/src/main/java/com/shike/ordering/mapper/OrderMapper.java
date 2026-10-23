package com.shike.ordering.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shike.ordering.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Order 的持久层。
 *
 * <p>继承 BaseMapper 就拿到了增删改查；统计类查询用 LambdaQuery 表达不了
 * （GROUP BY + 聚合函数），所以在下面手写 SQL。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 按日汇总营业额。
     *
     * <p>已取消的单不计入 —— 那是没成交的钱，算进营业额会让报表虚高，
     * 而且和"今日营业额"这个口径对不上。
     *
     * @param from 起始时间（含）
     */
    @Select("""
            SELECT DATE(created_at) AS `day`, SUM(total) AS amount, COUNT(*) AS orders
            FROM t_order
            WHERE deleted = 0 AND status <> 'CANCELLED' AND created_at >= #{from}
            GROUP BY DATE(created_at)
            ORDER BY day
            """)
    List<Map<String, Object>> revenueByDay(@Param("from") LocalDateTime from);

    /**
     * 分类销售占比，后台饼图用。
     *
     * <p>按<b>金额</b>而不是按份数：一份 68 元的火锅和一份 12 元的包子
     * 各算一单的话，占比完全反映不出营收结构。
     */
    @Select("""
            SELECT c.name AS name, SUM(oi.amount) AS amount
            FROM t_order_item oi
            JOIN t_order o ON o.id = oi.order_id AND o.deleted = 0 AND o.status <> 'CANCELLED'
            JOIN t_dish d ON d.id = oi.dish_id
            JOIN t_category c ON c.id = d.category_id
            WHERE o.created_at >= #{from}
            GROUP BY c.id, c.name
            ORDER BY amount DESC
            """)
    List<Map<String, Object>> categoryShare(@Param("from") LocalDateTime from);

    /**
     * 一段时间内的整体经营指标。
     *
     * <p>返回一行，字段是 orders（单数）和 amount（营业额）。
     * 调用方负责做除法算客单价 —— 除零也要调用方处理，
     * 因为"这段时间一单都没有"是正常情况而不是错误。
     */
    @Select("""
            SELECT COUNT(*) AS orders, COALESCE(SUM(total), 0) AS amount
            FROM t_order
            WHERE deleted = 0 AND status <> 'CANCELLED'
              AND created_at >= #{from} AND created_at < #{to}
            """)
    Map<String, Object> summary(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 各状态的订单数，后台首页那几个小卡片用 */
    @Select("""
            SELECT status, COUNT(*) AS cnt
            FROM t_order
            WHERE deleted = 0 AND created_at >= #{from}
            GROUP BY status
            """)
    List<Map<String, Object>> countByStatus(@Param("from") LocalDateTime from);
}
