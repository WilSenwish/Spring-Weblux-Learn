package com.example.accounting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillQueryRequest {
    private Integer type;
    private Long categoryId;
    private Long ledgerId;
    private LocalDate startDate;
    private LocalDate endDate;
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int size = 10;
}
