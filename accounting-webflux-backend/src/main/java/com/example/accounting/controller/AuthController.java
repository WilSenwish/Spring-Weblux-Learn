package com.example.accounting.controller;

import com.example.accounting.dto.LoginRequest;
import com.example.accounting.dto.LoginResponse;
import com.example.accounting.dto.RegisterRequest;
import com.example.accounting.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public Mono<String> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public Mono<String> refresh(@RequestHeader("Authorization") String authHeader) {
        return authService.refresh(authHeader);
    }
}
