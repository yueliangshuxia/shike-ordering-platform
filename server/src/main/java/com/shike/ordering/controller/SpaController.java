package com.shike.ordering.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 把 Vue 的 history 路由交还给前端。
 *
 * <p>前端用的是 {@code createWebHistory()}，所以 {@code /menu}、{@code /dish/D1001}
 * 这些都是浏览器的真实地址。开发时 Vite 自己处理；打成 jar 之后由 Spring 提供静态资源，
 * 服务器上并不存在 {@code menu} 这个文件，直接访问会 404 —— 刷新页面就白屏。
 *
 * <p>这里只兜住**不带点**的路径（静态资源都带扩展名），把它们 forward 到 index.html，
 * 剩下的交给 Vue Router 解析。{@code /api} 和 {@code /ws} 必须排除：
 * 否则调错一个接口地址会收到一张 HTML 页面而不是 404 JSON，排查时非常误导。
 */
@Controller
public class SpaController {

    @GetMapping({"/{path:[^\\.]*}", "/{path:[^\\.]*}/{sub:[^\\.]*}"})
    public String forward(HttpServletRequest request) throws NoResourceFoundException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api") || uri.startsWith("/ws")) {
            throw new NoResourceFoundException(HttpMethod.GET, uri);
        }
        return "forward:/index.html";
    }
}
