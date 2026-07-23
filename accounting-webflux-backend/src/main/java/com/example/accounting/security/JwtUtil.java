package com.example.accounting.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Webflux JWT 工具类，继承公共模块，添加 Mono 响应式包装
 */
@Component
public class JwtUtil extends com.example.accounting.common.security.JwtUtil {

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        super(secret, expiration);
    }

    /** 响应式生成 token */
    public Mono<String> generateTokenReactive(String username) {
        return Mono.fromCallable(() -> generateToken(username));
    }

    /** 响应式提取用户名 */
    public Mono<String> extractUsernameReactive(String token) {
        return Mono.fromCallable(() -> extractUsername(token))
                .onErrorResume(e -> Mono.empty());
    }

    /** 响应式验证 token */
    public Mono<Boolean> validateTokenReactive(String token, String username) {
        return extractUsernameReactive(token)
                .flatMap(extractedUsername -> isTokenExpiredReactive(token)
                        .map(expired -> extractedUsername.equals(username) && !expired))
                .defaultIfEmpty(false);
    }

    /** 响应式检查 token 是否过期 */
    public Mono<Boolean> isTokenExpiredReactive(String token) {
        return Mono.fromCallable(() -> isTokenExpired(token))
                .onErrorResume(e -> Mono.just(true));
    }
}
