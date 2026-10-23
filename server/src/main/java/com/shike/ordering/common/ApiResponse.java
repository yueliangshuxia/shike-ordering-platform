package com.shike.ordering.common;

/**
 * 统一响应体。前端只认 {@code code == 0} 为成功，其余一律走错误分支。
 *
 * <p>HTTP 状态码仍然是有意义的（401 未登录、403 无权限会照常返回），
 * 但业务层面的失败（库存不足、订单已支付）统一用 {@code code} 表达，
 * 免得前端要在每个地方去猜 4xx 到底是"没登录"还是"菜卖完了"。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static final int CODE_OK = 0;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(CODE_OK, "ok", data);
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
