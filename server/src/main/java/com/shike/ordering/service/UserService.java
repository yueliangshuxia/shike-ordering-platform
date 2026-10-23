package com.shike.ordering.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shike.ordering.common.BizException;
import com.shike.ordering.common.ErrorCode;
import com.shike.ordering.dto.LoginRequest;
import com.shike.ordering.dto.LoginResponse;
import com.shike.ordering.dto.RegisterRequest;
import com.shike.ordering.dto.UserVO;
import com.shike.ordering.entity.Order;
import com.shike.ordering.entity.User;
import com.shike.ordering.mapper.OrderMapper;
import com.shike.ordering.mapper.UserMapper;
import com.shike.ordering.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号与登录。
 *
 * <p>不做 Service 接口 + Impl 的两层拆分：这里每个方法只有一个实现，
 * 接口带来的唯一好处是"将来可能换实现"，而本项目不会换。
 * 多一层反而让 IDEA 里跳转多一次、论文里的类图多一个没有信息量的框。
 */
@Service
public class UserService {

    /** 「常客」和「熟客」的门槛订单数。堂食场景下单频次不高，5 单已经能算熟脸了 */
    private static final int REGULAR_THRESHOLD = 3;
    private static final int FAMILIAR_THRESHOLD = 8;

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserMapper userMapper, OrderMapper orderMapper,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (findByUsername(req.username()) != null) {
            throw new BizException(ErrorCode.USERNAME_TAKEN, "这个手机号已经注册过了");
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setNickname(req.nickname());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? req.username() : req.phone());
        u.setRole("CUSTOMER");
        u.setLevel("新客");
        userMapper.insert(u);
        return new LoginResponse(jwtService.issue(u), jwtService.ttlSeconds(), UserVO.of(u));
    }

    public LoginResponse login(LoginRequest req) {
        User u = findByUsername(req.username());
        // 账号不存在和密码错误返回同一句话：分开说等于告诉别人哪些手机号注册过
        if (u == null || !passwordEncoder.matches(req.password(), u.getPassword())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "账号或密码不对");
        }
        return new LoginResponse(jwtService.issue(u), jwtService.ttlSeconds(), UserVO.of(u));
    }

    public User findByUsername(String username) {
        return userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username).last("limit 1"));
    }

    public User requireById(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return u;
    }

    public UserVO profile(Long id) {
        return UserVO.of(requireById(id));
    }

    @Transactional
    public UserVO updateProfile(Long id, String nickname, String phone, String avatar) {
        User u = requireById(id);
        if (nickname != null && !nickname.isBlank()) {
            u.setNickname(nickname);
        }
        if (phone != null) {
            u.setPhone(phone);
        }
        if (avatar != null) {
            u.setAvatar(avatar);
        }
        userMapper.updateById(u);
        return UserVO.of(u);
    }

    /**
     * 按历史订单数刷新会员等级。
     *
     * <p>下单之后调一次即可。不做成定时任务是因为这个值只在
     * 「个人中心展示」和「订单页显示」两处用到，实时性要求很低，
     * 而定时扫全表算等级纯属浪费。
     */
    @Transactional
    public void refreshLevel(Long userId) {
        Long count = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .ne(Order::getStatus, "CANCELLED"));

        String level = count >= FAMILIAR_THRESHOLD ? "熟客"
                : count >= REGULAR_THRESHOLD ? "常客" : "新客";

        User u = userMapper.selectById(userId);
        if (u != null && !level.equals(u.getLevel())) {
            u.setLevel(level);
            userMapper.updateById(u);
        }
    }
}
