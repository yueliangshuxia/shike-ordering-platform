package com.shike.ordering.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从请求头里取 JWT，验过之后把登录人放进 SecurityContext。
 *
 * <p>拿不到令牌或令牌无效时<b>什么都不做</b>，直接放行 —— 让后面的
 * 授权规则去决定这个接口要不要登录。如果这里就抛 401，
 * 那么匿名可访问的接口（菜单、推荐）也会被拦掉。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            jwtService.parse(token).ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(LoginUser user) {
        // 角色存的是 ADMIN / KITCHEN / CUSTOMER，而 Spring 的 hasRole("ADMIN")
        // 默认要找 ROLE_ADMIN 这个 authority，所以这里必须补上前缀
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
        var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
