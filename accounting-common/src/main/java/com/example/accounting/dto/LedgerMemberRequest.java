package com.example.accounting.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerMemberRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    private Integer role;
}
