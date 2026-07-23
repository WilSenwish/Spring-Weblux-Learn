package com.example.accounting.mapper;

import com.example.accounting.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    User findByUsername(String username);
    boolean existsByUsername(String username);
    void insert(User user);
    User findById(Long id);
}
