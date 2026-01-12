package org.example.user.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.user.config.JwtProperties;
import org.example.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtils {

    @Autowired
    private JwtProperties jwtProperties;

    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("id", user.getId())
                .claim("role", user.getRole())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims getClaims(String token) {
        // 关键点：如果前端传来的 token 包含 "Bearer "，需要截取掉
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从 Token 中获取用户名 (Subject)
     */
    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 从 Token 中获取用户 ID
     */
    public Integer getUserIdFromToken(String token) {
        Object id = getClaims(token).get("id");
        if (id == null) return null;

        return Integer.valueOf(id.toString());
    }
    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

}