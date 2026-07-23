package com.example.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillRequest {
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    private Long ledgerId;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "账单类型不能为空")
    private Integer type;

    private String remark;

    @NotNull(message = "账单日期不能为空")
    private LocalDate billDate;
}
