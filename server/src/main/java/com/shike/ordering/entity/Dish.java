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
 * 菜品。
 *
 * <p>标签和食材<b>不在这里</b>：它们分别落在 t_dish_tag / t_dish_ingredient，
 * 由 {@code DishService} 组装。实体保持和表一一对应，免得一个字段有时是
 * 逗号串、有时是集合，用起来要猜。
 */
@Data
@TableName("t_dish")
public class Dish {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 菜品编码，如 D1001。演示数据和前端都按它对齐 */
    private String code;

    private String name;

    private Long categoryId;

    /** 单价（元）。用 BigDecimal 而不是 double —— 钱不能有浮点误差 */
    private BigDecimal price;

    private String img;

    /** 一句话卖点 */
    private String brief;

    private String detail;

    /** 0 不辣 / 1 微辣 / 2 中辣 / 3 特辣 */
    private Integer spice;

    /** 近 30 天销量 */
    private Integer sold;

    private BigDecimal rating;

    /** 出餐时长（分钟） */
    private Integer prepMin;

    /** 剩余库存，0 表示售罄 */
    private Integer stock;

    private String unit;

    /** 规格选择器上方的标签，如「份量」「辣度」 */
    private String specLabel;

    private Integer enabled;

    /** 不赋值即可，数据库的 DEFAULT CURRENT_TIMESTAMP 会接管 */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;

    /** 库存为 0 就是售罄。判断只写这一处，别在别处再写一遍 stock <= 0 */
    public boolean soldOut() {
        return stock == null || stock <= 0;
    }
}
