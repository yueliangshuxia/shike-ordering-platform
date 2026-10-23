package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户。顾客、后厨、管理员共用一张表，靠 {@link #role} 区分。
 *
 * <p>{@code password} 存的是 BCrypt 摘要，任何时候都不应该被序列化进响应体 ——
 * 对外一律用 {@link com.shike.ordering.dto.UserVO}，不要直接返回这个实体。
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String phone;

    /** CUSTOMER / KITCHEN / ADMIN */
    private String role;

    /** 新客 / 常客 / 熟客 */
    private String level;

    private String avatar;

    /** 不赋值即可，数据库的 DEFAULT CURRENT_TIMESTAMP 会接管 */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
