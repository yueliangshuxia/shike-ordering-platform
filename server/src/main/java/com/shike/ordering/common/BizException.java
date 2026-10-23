package com.shike.ordering.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。抛出来由 {@link GlobalExceptionHandler} 统一转成响应体，
 * 业务代码里不要自己去拼 ApiResponse。
 *
 * <p>带上 {@link HttpStatus} 是因为有几种失败真的要区分：没登录要 401
 * （前端据此跳登录页并记住回跳地址），没权限要 403，
 * 而"库存不足"这种是 200 + 业务码 —— 请求本身没毛病，是业务状态不允许。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BizException(String message) {
        this(ErrorCode.BIZ_ERROR, message, HttpStatus.OK);
    }

    public BizException(int code, String message) {
        this(code, message, HttpStatus.OK);
    }

    public BizException(int code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
