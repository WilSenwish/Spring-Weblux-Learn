package com.example.accounting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryStat {
    private Long categoryId;
    private String categoryName;
    private Integer type;
    private BigDecimal amount;
    private Double percentage;
}
