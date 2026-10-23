package com.shike.ordering.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 后厨屏的 WebSocket 连接管理。
 *
 * <p>用<b>裸 WebSocket</b> 而不是 STOMP：这里只有一个服务端推、客户端收的场景，
 * 没有订阅主题、没有消息确认、没有权限频道，上 STOMP 只会多一层要学的东西
 * 和一个要配的端点前缀。协议越简单，答辩时越好讲。
 *
 * <p>{@link CopyOnWriteArraySet} 是必须的：连接和断开发生在 Tomcat 的
 * 工作线程上，而广播发生在业务线程上，两边同时动这个集合。
 * 用普通 {@code HashSet} 会在有人刷新页面（断开 + 重连）时抛
 * {@code ConcurrentModificationException}，而且只在后厨屏有多个的时候才复现，
 * 很难查。
 */
@Component
public class KitchenWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(KitchenWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("后厨屏接入 {}，当前在线 {}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("后厨屏断开 {}，当前在线 {}", session.getId(), sessions.size());
    }

    /**
     * 广播一条消息给所有后厨屏。
     *
     * <p>单个连接发送失败只记日志、不往外抛：一块屏的网络抖了一下，
     * 不该让下订单这个动作失败。发不出去的那块屏下次事件会自己跟上，
     * 因为客户端每次收到通知都会重新拉一遍完整列表（推的是"变了"，不是"变成什么"）。
     */
    public void broadcast(String json) {
        if (sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                // WebSocketSession 不是线程安全的，同一连接并发 sendMessage 会报错。
                // 同步在 session 对象上，保证一条连接同一时刻只有一个发送者
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("向后厨屏 {} 推送失败：{}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    public int onlineCount() {
        return sessions.size();
    }
}
