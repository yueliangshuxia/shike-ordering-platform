package com.shike.ordering.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常兜底。
 *
 * <p>两条原则：
 * <ol>
 *   <li>业务异常按它自己带的码和状态返回，前端能据此分流。</li>
 *   <li>非预期异常（NPE、SQL 报错）<b>只回一句通用提示，细节写日志</b> ——
 *       把堆栈丢给前端既不安全，对用户也毫无意义。</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    /** @Valid 校验失败。把字段名带上，否则用户不知道是哪个输入有问题 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + messageOf(f))
                .collect(Collectors.joining("；"));
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.PARAM_INVALID, detail));
    }

    /**
     * 请求体不是合法 JSON（或者编码不是 UTF-8）。
     *
     * <p>不处理的话会掉进下面的兜底分支，变成 500「服务器开小差了」——
     * 可这明明<b>是调用方的问题</b>。前端拿到 500 会去查后端日志，
     * 而真正的原因就在它自己发出去的那个请求里。
     * 归到 400 并把话说明白，排查方向才不会跑偏。
     *
     * <p>这个坑实际踩过：用 Windows 控制台的 curl 直接发含中文的
     * {@code -d '{"avoidIngredients":["牛羊肉"]}'}，控制台是 GBK，
     * 发出去的字节流不是合法 UTF-8，服务端报的就是
     * {@code Invalid UTF-8 middle byte 0xf2}。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID,
                        "请求体格式不对，需要是 UTF-8 编码的合法 JSON"));
    }

    /** Spring Security 拦下来的无权限访问，走 403 而不是 500 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN, "当前账号没有权限执行这个操作"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("未预期的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.BIZ_ERROR, "服务器开小差了，请稍后再试"));
    }

    private static String messageOf(FieldError f) {
        return f.getDefaultMessage() == null ? "不合法" : f.getDefaultMessage();
    }
}
