package com.example.accounting.controller;

import com.example.accounting.entity.User;
import com.example.accounting.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class BaseController {

    @Autowired
    protected UserMapper userMapper;

    protected Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        return user != null ? user.getId() : null;
    }
}
