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
@Table("ledger")
public class Ledger {
    @Id
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private Integer type;
    /** 成员是否可修改他人账单（1-允许 0-仅改自己） */
    private Integer allowMemberEdit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
