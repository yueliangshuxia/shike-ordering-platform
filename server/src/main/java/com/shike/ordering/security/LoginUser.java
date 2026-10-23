package com.shike.ordering.security;

/**
 * 放进 Spring Security 上下文里的当前登录人。
 *
 * <p>刻意做成一个不含密码的只读对象：实体 {@code User} 直接当 principal 的话，
 * 任何地方 {@code getPrincipal()} 都能拿到 BCrypt 摘要，一旦不小心被
 * 序列化进日志或响应体就是泄露。这里从类型上就杜绝了这件事。
 *
 * @param id       用户 id
 * @param username 登录账号
 * @param nickname 昵称
 * @param role     CUSTOMER / KITCHEN / ADMIN
 */
public record LoginUser(Long id, String username, String nickname, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isKitchen() {
        return "KITCHEN".equals(role) || isAdmin();
    }
}
