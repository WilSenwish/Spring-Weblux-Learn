package com.example.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {
    @NotBlank(message = "分类名称不能为空")
    private String name;

    @NotNull(message = "分类类型不能为空")
    private Integer type;

    /** 账本ID（NULL表示全局可见） */
    private Long ledgerId;
}
