package com.shike.ordering.security;

import com.shike.ordering.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 的签发与校验。
 *
 * <p>用 HS256 对称签名：单机部署、只有自己验自己的令牌，对称密钥比 RSA 简单得多，
 * 而且不用管密钥文件的分发。
 *
 * <p><b>密钥长度是硬要求</b>：HS256 要求至少 256 位，短了 jjwt 会直接抛
 * {@code WeakKeyException}。所以 {@code shike.jwt.secret} 必须写够 32 字节 ——
 * 这不是"建议值"，是启动就会炸的约束。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${shike.jwt.secret}") String secret,
                      @Value("${shike.jwt.expire-hours:12}") long expireHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "shike.jwt.secret 太短（" + bytes.length + " 字节），HS256 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofHours(expireHours);
    }

    /** 签发令牌。只放身份信息，不放权限明细 —— 权限每次从角色推出来，免得改权限要等令牌过期 */
    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解出登录人。
     *
     * <p>令牌过期、签名不对、格式不对，一律返回空 —— 调用方只关心
     * "这个令牌能不能用"，不需要区分失败原因（区分了反而给攻击者信息）。
     */
    public Optional<LoginUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new LoginUser(
                    Long.valueOf(c.getSubject()),
                    c.get("username", String.class),
                    c.get("nickname", String.class),
                    c.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
