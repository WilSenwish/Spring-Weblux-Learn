package com.example.accounting.service;

import com.example.accounting.common.BusinessException;
import com.example.accounting.dto.LoginRequest;
import com.example.accounting.dto.LoginResponse;
import com.example.accounting.dto.RegisterRequest;
import com.example.accounting.entity.Ledger;
import com.example.accounting.entity.LedgerMember;
import com.example.accounting.entity.User;
import com.example.accounting.mapper.LedgerMapper;
import com.example.accounting.mapper.LedgerMemberMapper;
import com.example.accounting.mapper.UserMapper;
import com.example.accounting.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private LedgerMapper ledgerMapper;

    @Autowired
    private LedgerMemberMapper ledgerMemberMapper;

    /**
     * 注册用户并自动创建默认个人账本
     */
    @Transactional
    public String register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的密码不一致");
        }
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new BusinessException(400, "用户名已存在");
        }
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userMapper.insert(user);

        // 创建默认个人账本
        Ledger defaultLedger = Ledger.builder()
                .name("默认账本")
                .description("系统自动创建的个人账本")
                .ownerId(user.getId())
                .type(1)
                .allowMemberEdit(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ledgerMapper.insert(defaultLedger);

        // 插入所有者成员记录
        LedgerMember member = LedgerMember.builder()
                .ledgerId(defaultLedger.getId())
                .userId(user.getId())
                .role(1)
                .joinedAt(LocalDateTime.now())
                .build();
        ledgerMemberMapper.insert(member);

        return "注册成功";
    }

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(request.getUsername());
        return LoginResponse.builder()
                .token(token)
                .expiresIn(jwtUtil.getExpiration())
                .build();
    }

    public String refresh(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new BusinessException(401, "Token无效或已过期");
        }
        String actualToken = token.substring(7);
        String username = jwtUtil.extractUsername(actualToken);
        if (username == null) {
            throw new BusinessException(401, "Token无效或已过期");
        }
        if (!jwtUtil.validateToken(actualToken, username)) {
            throw new BusinessException(401, "Token无效或已过期");
        }
        return jwtUtil.generateToken(username);
    }
}
