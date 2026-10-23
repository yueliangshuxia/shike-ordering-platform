package com.shike.ordering.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.common.ErrorCode;
import com.shike.ordering.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 鉴权与放行规则。
 *
 * <p>几个刻意的选择：
 *
 * <ul>
 *   <li><b>菜单和推荐接口匿名可访问</b>。推荐算法对新客友好是这个方案的核心卖点，
 *       如果先要登录才能看推荐，这个卖点在演示时根本体现不出来。
 *       游客进来就能看到带理由的推荐，登录之后推荐会更准 —— 这才是卖点该有的样子。</li>
 *   <li><b>不用 Session</b>。无状态 + JWT，后厨屏、顾客手机、后台管理可以同时在线，
 *       服务端不用存会话。</li>
 *   <li><b>401 和 403 分开</b>。401 前端跳登录页并记住回跳地址；
 *       403 只提示没权限，不跳登录页 —— 跳了也没用，账号是好的。</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsSource;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper,
                          @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
        this.corsSource = corsSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 自带随机盐，同一个密码每次摘要都不同，
        // 所以校验只能用它自己的 matches()，不能拿摘要去比字符串
        return new BCryptPasswordEncoder();
    }

    /**
     * 注意 {@code corsSource} 是从字段传进来的、而不是写在方法参数上。
     *
     * <p>写成方法参数会启动失败：{@code CorsConfigurationSource} 这个类型有两个候选 ——
     * 我们自己的这个，和 Spring MVC 自动装配的 {@code mvcHandlerMappingIntrospector}
     * （它也实现了这个接口）。按类型注入时 Spring 不会替你挑，
     * 直接把整个应用起不来。构造器上加 {@code @Qualifier} 才定得下来。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 只放行登录和注册两个接口。
                        // 不能写成 "/api/auth/**" —— 那会连 /api/auth/me 和
                        // /api/auth/profile 一起放行，而未登录时 @AuthenticationPrincipal
                        // 注入的是 null，控制器里一句 me.id() 就变成 NPE，
                        // 前端拿到的是 500 而不是 401，登录态过期时完全不知道该跳登录页。
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/health").permitAll()
                        // 浏览菜单、看门店信息不需要登录
                        .requestMatchers(HttpMethod.GET,
                                "/api/categories/**", "/api/dishes/**", "/api/menu/**",
                                "/api/tables/**", "/api/shop").permitAll()
                        // 推荐也不需要登录：新客第一次打开就得出结果，这是本方案相对协同过滤的优势。
                        // POST 也必须放行 —— 「推荐实验室」拖滑杆调权重走的是 POST，
                        // 只放行 GET 的话游客一进实验室就被弹去登录页，
                        // 而这个页面恰恰是答辩时最能说明算法在干什么的地方。
                        // 它没有任何副作用（覆盖项不落库），放行是安全的。
                        .requestMatchers("/api/recommend/**").permitAll()
                        // 后厨屏
                        .requestMatchers("/api/kitchen/**").hasAnyRole("KITCHEN", "ADMIN")
                        // 经营看板
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // WebSocket 握手走同一个过滤器，鉴权交给握手时的拦截器
                        .requestMatchers("/ws/**").permitAll()
                        // 打进 jar 的 Vue 前端也要能直接打开。页面本身是公开的
                        // （登录态由前端路由守卫引导，真正的权限在后端接口），
                        // 静态资源和 /menu 这类前端路由都不该被 401 挡掉。
                        // 反过来写「放行 /api 以外的所有路径」比逐个列静态目录可靠 ——
                        // 以后前端多一个资源目录不用回来改这里。
                        .requestMatchers(request -> !request.getRequestURI().startsWith("/api")).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeJson(res, HttpServletResponse.SC_UNAUTHORIZED,
                                        ApiResponse.fail(ErrorCode.UNAUTHORIZED, "请先登录")))
                        .accessDeniedHandler((req, res, e) ->
                                writeJson(res, HttpServletResponse.SC_FORBIDDEN,
                                        ApiResponse.fail(ErrorCode.FORBIDDEN, "当前账号没有权限执行这个操作"))))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJson(HttpServletResponse res, int status, ApiResponse<?> body) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getWriter(), body);
    }
}
