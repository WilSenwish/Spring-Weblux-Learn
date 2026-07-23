package com.example.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerRequest {
    @NotBlank(message = "账本名称不能为空")
    private String name;
    private String description;
    private Integer type;
    /** 成员是否可修改他人账单（1-允许 0-仅改自己） */
    private Integer allowMemberEdit;
}
