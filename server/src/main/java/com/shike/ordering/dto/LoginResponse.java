package com.shike.ordering.dto;

/**
 * 登录/注册成功后返回的东西。
 *
 * <p>把 {@code expiresIn} 一并给前端，是为了让前端能提前刷新或提示重新登录，
 * 而不是等某个请求突然 401 才发现令牌过期 —— 那会打断用户正在做的操作，
 * 比如购物车算好价格点了提交才发现要重新登录。
 */
public record LoginResponse(String token, long expiresIn, UserVO user) {
}
