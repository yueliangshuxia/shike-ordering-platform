package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.dto.LoginRequest;
import com.shike.ordering.dto.LoginResponse;
import com.shike.ordering.dto.RegisterRequest;
import com.shike.ordering.dto.UserVO;
import com.shike.ordering.security.LoginUser;
import com.shike.ordering.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录、注册、个人资料。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(userService.login(req));
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(userService.register(req));
    }

    /**
     * 当前登录人。前端每次刷新都拿它探一次登录态 ——
     * 令牌存在 localStorage 里，但可能已经过期，不能光凭"有没有令牌"就认为登录着。
     */
    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal LoginUser me) {
        return ApiResponse.ok(userService.profile(me.id()));
    }

    @PutMapping("/profile")
    public ApiResponse<UserVO> updateProfile(@AuthenticationPrincipal LoginUser me,
                                             @RequestBody ProfileUpdate body) {
        return ApiResponse.ok(userService.updateProfile(me.id(), body.nickname(), body.phone(), body.avatar()));
    }

    /** 资料修改的入参。三个字段都允许不传，传了才改 */
    public record ProfileUpdate(String nickname, String phone, String avatar) {
    }
}
