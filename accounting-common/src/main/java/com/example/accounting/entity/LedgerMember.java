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
@Table("ledger_member")
public class LedgerMember {
    @Id
    private Long id;
    private Long ledgerId;
    private Long userId;
    private Integer role;
    private LocalDateTime joinedAt;
}
