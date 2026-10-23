package com.shike.ordering.dto;

import com.shike.ordering.entity.User;

/**
 * 对外的用户视图。
 *
 * <p>存在的唯一理由就是<b>不带密码字段</b>。直接返回 {@code User} 实体最省事，
 * 但那意味着任何一次接口字段的增删都可能把 BCrypt 摘要漏出去，
 * 而且一旦漏了很难在测试里发现。用一个专门的 VO，从类型上就不会发生。
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String phone,
        String role,
        String level,
        String avatar
) {

    public static UserVO of(User u) {
        return new UserVO(u.getId(), u.getUsername(), u.getNickname(),
                maskPhone(u.getPhone()), u.getRole(), u.getLevel(), u.getAvatar());
    }

    /** 手机号中间四位打码。页面上要显示，但没必要把完整号码发到前端 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
