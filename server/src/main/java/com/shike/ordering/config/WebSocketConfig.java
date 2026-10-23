package com.shike.ordering.config;

import com.shike.ordering.ws.KitchenWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册。
 *
 * <p>端点 {@code /ws/kitchen}，后厨屏连它拿实时单。
 *
 * <p>允许所有来源：开发时前端在 5173、后厨屏可能就是本机直接开的一个 HTML，
 * 来源五花八门。这里不放宽的话，浏览器会因为跨域直接拒绝握手，
 * 而报错信息（"WebSocket connection failed"）完全看不出是来源的问题。
 * 真正的访问控制由 {@code SecurityConfig} 里对 {@code /ws/**} 的规则负责 ——
 * 注意 WebSocket 握手是一次普通 HTTP 请求，会经过同一套过滤器链。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final KitchenWebSocketHandler kitchenHandler;

    public WebSocketConfig(KitchenWebSocketHandler kitchenHandler) {
        this.kitchenHandler = kitchenHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(kitchenHandler, "/ws/kitchen").setAllowedOriginPatterns("*");
    }
}
