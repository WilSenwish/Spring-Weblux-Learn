package com.example.accounting.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("category")
public class Category {
    @Id
    private Long id;
    private Long userId;
    /** 账本ID（NULL表示全局可见） */
    private Long ledgerId;
    private String name;
    private Integer type;
    private Integer isPreset;
    private LocalDateTime createdAt;
}
