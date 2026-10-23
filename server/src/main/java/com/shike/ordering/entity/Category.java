package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 菜品分类。
 *
 * <p>注意这里<b>没有</b>「招牌推荐」—— 它是虚拟分类，
 * 由 {@code DishService.listCategories()} 拼在结果最前面。
 * 详见 {@link com.shike.ordering.algorithm.DishFeature#CAT_SIGNATURE}。
 */
@Data
@TableName("t_category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类代号，算法和前端都用这个，如 dimsum */
    private String code;

    /** 显示名，如 蒸点 */
    private String name;

    private String icon;

    private Integer sort;

    private Integer enabled;
}
