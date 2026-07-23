package com.example.accounting.controller;

import com.example.accounting.entity.User;
import com.example.accounting.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public abstract class BaseController {

    @Autowired
    protected UserRepository userRepository;

    protected Mono<Long> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(username -> userRepository.findByUsername(username))
                .map(User::getId);
    }
}
