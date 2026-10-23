package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 菜品食材。忌口过滤的数据来源。
 *
 * <p>存的是食材名原文而不是编码 —— 算法做的是「子串 + 别名」匹配，
 * 勾了「牛羊肉」要能挡住「牛肉末」，靠的正是原文里的「牛」字。
 */
@Data
@TableName("t_dish_ingredient")
public class DishIngredient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    private String name;

    private Integer sort;
}
