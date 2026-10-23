package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 餐桌。
 */
@Data
@TableName("t_dining_table")
public class DiningTable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 桌号，如 A12。前端扫码进来带的就是它 */
    private String code;

    private Integer seats;

    private String area;

    /** FREE 空闲 / OCCUPIED 就餐中 / RESERVED 已预订 */
    private String status;

    private Integer enabled;
}
