package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品规格。
 *
 * <p>{@link #priceDelta} 是相对基准价的<b>加价</b>而不是绝对价：
 * 这样调整基础价时不用逐条改规格，也不会出现「改了一处忘了另一处」
 * 导致的规格比本体便宜。
 */
@Data
@TableName("t_dish_spec")
public class DishSpec {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    private String name;

    private BigDecimal priceDelta;

    private Integer sort;
}
