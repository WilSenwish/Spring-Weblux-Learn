package com.example.accounting.service;

import com.example.accounting.common.BusinessException;
import com.example.accounting.dto.LoginRequest;
import com.example.accounting.dto.LoginResponse;
import com.example.accounting.dto.RegisterRequest;
import com.example.accounting.entity.Ledger;
import com.example.accounting.entity.LedgerMember;
import com.example.accounting.entity.User;
import com.example.accounting.repository.LedgerMemberRepository;
import com.example.accounting.repository.LedgerRepository;
import com.example.accounting.repository.UserRepository;
import com.example.accounting.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ReactiveAuthenticationManager authenticationManager;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private LedgerMemberRepository ledgerMemberRepository;

    public Mono<String> register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return Mono.error(new BusinessException(400, "两次输入的密码不一致"));
        }
        return userRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new BusinessException(400, "用户名已存在"));
                    }
                    User user = User.builder()
                            .username(request.getUsername())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return userRepository.save(user)
                            .flatMap(u -> {
                                // 创建默认个人账本
                                Ledger defaultLedger = Ledger.builder()
                                        .name("默认账本")
                                        .description("系统自动创建的个人账本")
                                        .ownerId(u.getId())
                                        .type(1)
                                        .allowMemberEdit(1)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                                return ledgerRepository.save(defaultLedger)
                                        .flatMap(savedLedger -> {
                                            // 插入所有者成员记录
                                            LedgerMember member = LedgerMember.builder()
                                                    .ledgerId(savedLedger.getId())
                                                    .userId(u.getId())
                                                    .role(1)
                                                    .joinedAt(LocalDateTime.now())
                                                    .build();
                                            return ledgerMemberRepository.save(member)
                                                    .thenReturn("注册成功");
                                        });
                            });
                });
    }

    public Mono<LoginResponse> login(LoginRequest request) {
        return authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
                )
                .flatMap(authentication -> jwtUtil.generateTokenReactive(request.getUsername())
                        .map(token -> {
                            LoginResponse response = LoginResponse.builder()
                                    .token(token)
                                    .expiresIn(jwtUtil.getExpiration())
                                    .build();
                            return response;
                        }));
    }

    public Mono<String> refresh(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return Mono.error(new BusinessException(401, "Token无效或已过期"));
        }
        String actualToken = token.substring(7);
        return jwtUtil.extractUsernameReactive(actualToken)
                .flatMap(username -> jwtUtil.validateTokenReactive(actualToken, username)
                        .flatMap(valid -> {
                            if (Boolean.TRUE.equals(valid)) {
                                return jwtUtil.generateTokenReactive(username);
                            }
                            return Mono.error(new BusinessException(401, "Token无效或已过期"));
                        }))
                .switchIfEmpty(Mono.error(new BusinessException(401, "Token无效或已过期")));
    }
}
