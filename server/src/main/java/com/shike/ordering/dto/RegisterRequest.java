package com.shike.ordering.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>账号就用手机号。堂食系统里手机号是唯一的自然标识 ——
 * 找客人、发券、对账都要靠它，再单独编一套用户名纯属多此一举。
 */
public record RegisterRequest(

        @NotBlank(message = "请填写手机号")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不对")
        String username,

        @NotBlank(message = "请设置密码")
        @Size(min = 6, max = 32, message = "密码长度需要在 6–32 位之间")
        String password,

        @NotBlank(message = "请填写昵称")
        @Size(max = 32, message = "昵称过长")
        String nickname,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不对")
        String phone
) {
}
