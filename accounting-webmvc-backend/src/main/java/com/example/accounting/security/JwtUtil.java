package com.example.accounting.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WebMVC JWT 工具类，直接继承公共模块的同步实现
 */
@Component
public class JwtUtil extends com.example.accounting.common.security.JwtUtil {

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        super(secret, expiration);
    }
}
