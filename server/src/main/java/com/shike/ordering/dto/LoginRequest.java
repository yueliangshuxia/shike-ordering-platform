package com.shike.ordering.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 */
public record LoginRequest(

        @NotBlank(message = "请填写账号")
        @Size(max = 32, message = "账号过长")
        String username,

        @NotBlank(message = "请填写密码")
        @Size(max = 64, message = "密码过长")
        String password
) {
}
