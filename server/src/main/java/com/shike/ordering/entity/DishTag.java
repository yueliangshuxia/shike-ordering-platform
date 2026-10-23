package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 菜品标签。取值见 {@link com.shike.ordering.algorithm.DishFeature} 里的常量：
 * 招牌 / 素食 / 冰饮 / 现做。
 */
@Data
@TableName("t_dish_tag")
public class DishTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    private String tag;
}
