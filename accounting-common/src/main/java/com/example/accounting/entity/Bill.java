package com.example.accounting.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("bill")
public class Bill {
    @Id
    private Long id;
    private Long userId;
    private Long categoryId;
    private Long ledgerId;
    private BigDecimal amount;
    private Integer type;
    private String remark;
    private LocalDate billDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
