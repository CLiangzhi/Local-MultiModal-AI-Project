package com.example.ai.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    // 商业级随机加密密钥
    private static final Key KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long EXPIRATION_TIME = 86400000; // Token 24小时有效

    // 给登录成功的用户签发 Token
    public String generateToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY)
                .compact();
    }

    // 从前端传来的 Token 中解析出是哪个用户
    public String extractUserId(String token) {
        return Jwts.parserBuilder().setSigningKey(KEY).build()
                .parseClaimsJws(token).getBody().getSubject();
    }
}