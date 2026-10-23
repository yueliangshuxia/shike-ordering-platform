package com.shike.ordering.common;

/**
 * 业务错误码。分段是为了让日志和前端能一眼看出是哪一类问题，
 * 不要随便插队占用已有值。
 *
 * <pre>
 *   1xxx  通用 / 参数
 *   2xxx  账号与鉴权
 *   3xxx  菜品与分类
 *   4xxx  订单与支付
 * </pre>
 */
public final class ErrorCode {

    private ErrorCode() {}

    /** 兜底：没归类的业务失败 */
    public static final int BIZ_ERROR = 1000;
    public static final int PARAM_INVALID = 1001;
    public static final int NOT_FOUND = 1002;

    public static final int UNAUTHORIZED = 2001;
    public static final int FORBIDDEN = 2002;
    public static final int BAD_CREDENTIALS = 2003;
    public static final int USERNAME_TAKEN = 2004;

    public static final int DISH_NOT_FOUND = 3001;
    public static final int DISH_SOLD_OUT = 3002;
    public static final int STOCK_NOT_ENOUGH = 3003;
    public static final int CATEGORY_NOT_FOUND = 3004;

    public static final int ORDER_NOT_FOUND = 4001;
    public static final int ORDER_STATUS_ILLEGAL = 4002;
    public static final int CART_EMPTY = 4003;
    public static final int TABLE_NOT_FOUND = 4004;
}
